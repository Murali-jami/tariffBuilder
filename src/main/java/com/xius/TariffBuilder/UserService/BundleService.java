package com.xius.TariffBuilder.UserService;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
public class BundleService {

    private static final Logger logger = LoggerFactory.getLogger(BundleService.class);

    @Autowired
    @Qualifier("oracleJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    public static class CloneAtpResult {

        private Long newAtpId;
        private String oldBucketId;
        private String newBucketId;
        private Long newBucketZoneId;

        public CloneAtpResult(Long newAtpId, String oldBucketId, String newBucketId, Long newBucketZoneId) {
            this.newAtpId = newAtpId;
            this.oldBucketId = oldBucketId;
            this.newBucketId = newBucketId;
            this.newBucketZoneId = newBucketZoneId;
        }

        public Long getNewAtpId() {
            return newAtpId;
        }

        public String getOldBucketId() {
            return oldBucketId;
        }

        public String getNewBucketId() {
            return newBucketId;
        }

        public Long getNewBucketZoneId() {
            return newBucketZoneId;
        }
    }

    /*
     * Get first old bucket id from ATP.
     */
    public String getOldBucketId(Long atpId, Long networkId) {

        return jdbcTemplate.query(
                """
                select bm.BUCKET_ID
                from CS_ATP_ACCUMU_BON_DISC_MAP am
                join CS_BNDL_MT_BNDL_BUCKET_MAP bm
                  on bm.BUNDLE_ID = am.BUNDLE_OR_DISCOUNT_ID
                where am.ATP_ID = ?
                and am.NETWORK_ID = ?
                fetch first 1 rows only
                """,
                rs -> rs.next() ? rs.getString("BUCKET_ID") : null,
                atpId,
                networkId
        );
    }

    /*
     * Get zone id from old bucket.
     */
    public Long getBucketZoneId(String bucketId) {

        if (bucketId == null) {
            return null;
        }

        return jdbcTemplate.query(
                """
                select ZONE_GROUP_ID
                from BNDL_MT_BUCKETS
                where BUCKET_ID = ?
                """,
                rs -> rs.next() ? rs.getObject("ZONE_GROUP_ID", Long.class) : null,
                bucketId
        );
    }

    /*
     * Generate new bucket zone id based on bucket table.
     */
    public Long generateNewBucketZoneId() {

        return jdbcTemplate.queryForObject(
                """
                select nvl(max(ZONE_GROUP_ID),0)+1
                from BNDL_MT_BUCKETS
                """,
                Long.class
        );
    }

    /*
     * Clone ATP, bundle, and bucket.
     * Bucket will use newBucketZoneId.
     */
    @Transactional
    public CloneAtpResult cloneAtpData(Long atpId,
                                       Long networkId,
                                       String tpName,
                                       Long newBucketZoneId) {

        logger.info("ATP clone started atpId={} networkId={} tpName={} newBucketZoneId={}",
                atpId, networkId, tpName, newBucketZoneId);

        Long newAtpId = jdbcTemplate.queryForObject(
                """
                select nvl(max(SERVICE_PACKAGE_ID),0)+1
                from CS_RAT_SERVICE_PACKAGE
                """,
                Long.class
        );

        Map<String, Object> oldAtp = jdbcTemplate.queryForMap(
                """
                select *
                from CS_RAT_SERVICE_PACKAGE
                where SERVICE_PACKAGE_ID = ?
                """,
                atpId
        );

        jdbcTemplate.update(
                """
                insert into CS_RAT_SERVICE_PACKAGE
                (
                    SERVICE_PACKAGE_ID,
                    SERVICE_PACKAGE_DESC,
                    NETWORK_ID
                )
                values (?,?,?)
                """,
                newAtpId,
                oldAtp.get("SERVICE_PACKAGE_DESC") + "_" + tpName,
                networkId
        );

        List<Long> bundleIds = jdbcTemplate.queryForList(
                """
                select BUNDLE_OR_DISCOUNT_ID
                from CS_ATP_ACCUMU_BON_DISC_MAP
                where ATP_ID = ?
                and NETWORK_ID = ?
                """,
                Long.class,
                atpId,
                networkId
        );

        String firstOldBucketId = null;
        String firstNewBucketId = null;

        for (Long oldBundleId : bundleIds) {

            Long newBundleId = cloneBundle(oldBundleId, networkId, tpName);

            List<String> bucketIds = jdbcTemplate.queryForList(
                    """
                    select BUCKET_ID
                    from CS_BNDL_MT_BNDL_BUCKET_MAP
                    where BUNDLE_ID = ?
                    """,
                    String.class,
                    oldBundleId
            );

            for (String oldBucketId : bucketIds) {

                String newBucketId = generateNewBucketId();

                cloneBucket(
                        oldBucketId,
                        newBucketId,
                        tpName,
                        networkId,
                        newBucketZoneId
                );

                jdbcTemplate.update(
                        """
                        insert into CS_BNDL_MT_BNDL_BUCKET_MAP
                        (
                            BUNDLE_ID,
                            BUCKET_ID,
                            NETWORK_ID
                        )
                        values (?,?,?)
                        """,
                        newBundleId,
                        newBucketId,
                        networkId
                );

                if (firstOldBucketId == null) {
                    firstOldBucketId = oldBucketId;
                    firstNewBucketId = newBucketId;
                }

                logger.info("Bucket cloned oldBucketId={} newBucketId={} newBucketZoneId={}",
                        oldBucketId, newBucketId, newBucketZoneId);
            }

            jdbcTemplate.update(
                    """
                    insert into CS_ATP_ACCUMU_BON_DISC_MAP
                    (
                        BUNDLE_OR_DISCOUNT_ID,
                        ATP_ID,
                        PLAN_TYPE,
                        NETWORK_ID
                    )
                    values (?,?,?,?)
                    """,
                    newBundleId,
                    newAtpId,
                    "B",
                    networkId
            );

            copyServiceAtpMap(atpId, newAtpId, networkId);
        }

        logger.info("ATP clone completed oldAtpId={} newAtpId={}", atpId, newAtpId);

        return new CloneAtpResult(
                newAtpId,
                firstOldBucketId,
                firstNewBucketId,
                newBucketZoneId
        );
    }

    private Long cloneBundle(Long oldBundleId, Long networkId, String tpName) {

        Map<String, Object> bundle = jdbcTemplate.queryForMap(
                """
                select *
                from BNDL_MT_BUNDLE
                where BUNDLE_ID = ?
                """,
                oldBundleId
        );

        Long newBundleId = jdbcTemplate.queryForObject(
                """
                select nvl(max(BUNDLE_ID),0)+1
                from BNDL_MT_BUNDLE
                """,
                Long.class
        );

        jdbcTemplate.update(
                """
                insert into BNDL_MT_BUNDLE
                (
                    BUNDLE_ID,
                    BUNDLE_NAME,
                    BUNDLE_CHARGE,
                    PURCHAGE_TYPE,
                    VALID_FROM,
                    VALID_UPTO,
                    CUSTOMER_GROUP_ID,
                    BUNDLE_STATUS,
                    STATUS_DATE,
                    CREATED_DATE,
                    CREATED_BY,
                    NETWORK_ID
                )
                values (?,?,?,?,?,?,?,?,?,sysdate,?,?)
                """,
                newBundleId,
                bundle.get("BUNDLE_NAME") + "_" + tpName,
                bundle.get("BUNDLE_CHARGE"),
                bundle.get("PURCHAGE_TYPE"),
                bundle.get("VALID_FROM"),
                bundle.get("VALID_UPTO"),
                bundle.get("CUSTOMER_GROUP_ID"),
                bundle.get("BUNDLE_STATUS"),
                bundle.get("STATUS_DATE"),
                bundle.get("CREATED_BY"),
                networkId
        );

        return newBundleId;
    }

    private String generateNewBucketId() {

        return jdbcTemplate.queryForObject(
                """
                select 'T' || (nvl(max(to_number(substr(BUCKET_ID,2))),0)+1)
                from BNDL_MT_BUCKETS
                where regexp_like(substr(BUCKET_ID,2),'^\\d+$')
                """,
                String.class
        );
    }

    private void cloneBucket(String oldBucketId,
                             String newBucketId,
                             String tpName,
                             Long networkId,
                             Long newBucketZoneId) {

        /*
         * Full bucket clone.
         * ZONE_GROUP_ID is replaced with newBucketZoneId.
         */
        jdbcTemplate.update(
                """
                insert into BNDL_MT_BUCKETS
                
                select
                    ?,
                    BUCKET_NAME || '_' || ?,
                    BALANCE_CATEGORY,
                    USAGE_TYPE,
                    VALIDITY_PERIOD_DAYS,
                    BUCKET_UNIT_VALUE,
                    BUCKET_UNIT_TYPE,
                    ITERATIVE_BUCKET_YN,
                    ITERATIVE_COUNTS,
                    ROLL_OVER_YN,
                    EXTEND_VALIDITY_YN,
                    BUCKET_STATUS,
                    STATUS_DATE,
                    SYSDATE,
                    CREATED_BY,
                    ?,
                    DURATION,
                    UNLIMITED_USAGE_YN,
                    EXPIRY_NOTIFICATION_TYPE,
                    EXPIRY_NOTIFICATION_THRESHOLD,
                    APLICABLE_FROM_HRS,
                    APLICABLE_TO_HRS,
                    LIMITED_HOURS,
                    BALANCE_ID,
                    LIMITED_NETWORKS_YN,
                    DATA_ZONE_GROUP_ID,
                    ?,
                    COUNTRY_ISD_PREFIX,
                    PRIORITY,
                    DEVICE_GROUP_ID,
                    IDD_GROUPID,
                    LOW_BAL_THRESHOLD1,
                    LOW_BAL_THRESHOLD2,
                    LOW_BAL_THRESHOLD3,
                    LOW_BAL_THRESHOLD4,
                    LOW_BAL_THRESHOLD5,
                    LOW_BAL_THRESHOLD6,
                    LOW_BAL_NOTIF_TYPE1,
                    LOW_BAL_NOTIF_TYPE2,
                    LOW_BAL_NOTIF_TYPE3,
                    LOW_BAL_NOTIF_TYPE4,
                    LOW_BAL_NOTIF_TYPE5,
                    LOW_BAL_NOTIF_TYPE6,
                    LOW_BAL_NOTIF_MSG1,
                    LOW_BAL_NOTIF_MSG2,
                    LOW_BAL_NOTIF_MSG3,
                    LOW_BAL_NOTIF_MSG4,
                    LOW_BAL_NOTIF_MSG5,
                    LOW_BAL_NOTIF_MSG6,
                    LOW_BAL_NOTIF_API1,
                    LOW_BAL_NOTIF_API2,
                    LOW_BAL_NOTIF_API3,
                    LOW_BAL_NOTIF_API4,
                    LOW_BAL_NOTIF_API5,
                    LOW_BAL_NOTIF_API6,
                    DAY_TYPE,
                    LOW_BAL_NOTIF_API_EXT1,
                    LOW_BAL_NOTIF_API_EXT2,
                    LOW_BAL_NOTIF_API_EXT3,
                    LOW_BAL_NOTIF_API_EXT4,
                    LOW_BAL_NOTIF_API_EXT5,
                    LOW_BAL_NOTIF_API_EXT6
                from BNDL_MT_BUCKETS
                where BUCKET_ID = ?
                """,
                newBucketId,
                tpName,
                networkId,
                newBucketZoneId,
                oldBucketId
        );
    }

    private void copyServiceAtpMap(Long oldAtpId, Long newAtpId, Long networkId) {

        List<Map<String, Object>> serviceMapList = jdbcTemplate.queryForList(
                """
                select BASIC_SERVICE_ID, DERIVED_SERVICE_ID
                from CS_RAT_SERVICE_ATP_MAP
                where SERVICE_PACKAGE_ID = ?
                and NETWORK_ID = ?
                """,
                oldAtpId,
                networkId
        );

        for (Map<String, Object> row : serviceMapList) {

            jdbcTemplate.update(
                    """
                    insert into CS_RAT_SERVICE_ATP_MAP
                    (
                        NETWORK_ID,
                        SERVICE_PACKAGE_ID,
                        BASIC_SERVICE_ID,
                        DERIVED_SERVICE_ID
                    )
                    values (?,?,?,?)
                    """,
                    networkId,
                    newAtpId,
                    row.get("BASIC_SERVICE_ID"),
                    row.get("DERIVED_SERVICE_ID")
            );
        }
    }
}
