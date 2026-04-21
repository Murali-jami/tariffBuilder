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
 
    private static final Logger logger =
            LoggerFactory.getLogger(BundleService.class);
 
    @Autowired
    @Qualifier("oracleJdbcTemplate")
    private JdbcTemplate jdbcTemplate;
 
    @Transactional
    public Long cloneAtpData(Long atpId, Long networkId, String tpName) {
 
        logger.info("Starting ATP clone process atpId={} networkId={} tpName={}",
                atpId, networkId, tpName);
 
        /*
         * STEP 1 generate NEW ATP id
         */
        Long newAtpId = jdbcTemplate.queryForObject(
                """
                SELECT NVL(MAX(SERVICE_PACKAGE_ID),0)+1
                FROM CS_RAT_SERVICE_PACKAGE
                """,
                Long.class);
 
        logger.debug("Generated newAtpId={}", newAtpId);
 
        /*
         * STEP 2 read OLD ATP
         */
        Map<String, Object> oldAtp = jdbcTemplate.queryForMap(
                """
                SELECT *
                FROM CS_RAT_SERVICE_PACKAGE
                WHERE SERVICE_PACKAGE_ID = ?
                """,
                atpId);
 
        logger.debug("Fetched old ATP data for atpId={}", atpId);
 
        /*
         * STEP 3 insert NEW ATP
         */
        jdbcTemplate.update(
                """
                INSERT INTO CS_RAT_SERVICE_PACKAGE
                (
                SERVICE_PACKAGE_ID,
                SERVICE_PACKAGE_DESC,
                NETWORK_ID
                )
                VALUES
                (?,?,?)
                """,
                newAtpId,
                oldAtp.get("SERVICE_PACKAGE_DESC") + "_" + tpName,
                networkId);
 
        logger.info("Inserted new ATP record newAtpId={}", newAtpId);
 
        /*
         * STEP 4 get bundles linked to OLD ATP
         */
        List<Long> bundleIds = jdbcTemplate.queryForList(
                """
                SELECT BUNDLE_OR_DISCOUNT_ID
                FROM CS_ATP_ACCUMU_BON_DISC_MAP
                WHERE ATP_ID = ?
                AND NETWORK_ID = ?
                """,
                Long.class,
                atpId,
                networkId);
 
        logger.info("Found {} bundles linked to ATP {}", bundleIds.size(), atpId);
 
        /*
         * STEP 5 clone each bundle
         */
        for (Long oldBundleId : bundleIds) {
 
            logger.debug("Cloning bundle oldBundleId={}", oldBundleId);
 
            Map<String, Object> bundle = jdbcTemplate.queryForMap(
                    """
                    SELECT *
                    FROM BNDL_MT_BUNDLE
                    WHERE BUNDLE_ID = ?
                    """,
                    oldBundleId);
 
            Long newBundleId = jdbcTemplate.queryForObject(
                    """
                    SELECT NVL(MAX(BUNDLE_ID),0)+1
                    FROM BNDL_MT_BUNDLE
                    """,
                    Long.class);
 
            jdbcTemplate.update(
                    """
                    INSERT INTO BNDL_MT_BUNDLE
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
                    VALUES
                    (?,?,?,?,?,?,?,?,?,SYSDATE,?,?)
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
                    networkId);
 
            logger.info("Inserted new bundle newBundleId={}", newBundleId);
 
            /*
             * STEP 6 get buckets linked to bundle
             */
            List<String> bucketIds = jdbcTemplate.queryForList(
                    """
                    SELECT BUCKET_ID
                    FROM CS_BNDL_MT_BNDL_BUCKET_MAP
                    WHERE BUNDLE_ID = ?
                    """,
                    String.class,
                    oldBundleId);
 
            logger.debug("Found {} buckets for bundle {}", bucketIds.size(), oldBundleId);
 
            /*
             * STEP 7 clone each bucket
             */
            for (String oldBucketId : bucketIds) {
 
                logger.debug("Cloning bucket oldBucketId={}", oldBucketId);
 
                Map<String, Object> bucket = jdbcTemplate.queryForMap(
                        """
                        SELECT *
                        FROM BNDL_MT_BUCKETS
                        WHERE BUCKET_ID = ?
                        """,
                        oldBucketId);
 
                String newBucketId = jdbcTemplate.queryForObject(
                        """
                        SELECT
                        'T' ||
                        (
                        NVL(
                        MAX(
                        TO_NUMBER(
                        SUBSTR(BUCKET_ID,2)
                        )
                        ),0
                        )+1
                        )
                        FROM BNDL_MT_BUCKETS
                        WHERE REGEXP_LIKE
                        (
                        SUBSTR(BUCKET_ID,2),
                        '^\\d+$'
                        )
                        """,
                        String.class);
 
                jdbcTemplate.update(
                        """
                        INSERT INTO BNDL_MT_BUCKETS
                        (
                        BUCKET_ID,
                        BUCKET_NAME,
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
                        CREATED_DATE,
                        CREATED_BY,
                        NETWORK_ID
                        )
                        VALUES
                        (?,?,?,?,?,?,?,?,?,?,?,?,?,SYSDATE,?,?)
                        """,
                        newBucketId,
                        bucket.get("BUCKET_NAME") + "_" + tpName,
                        bucket.get("BALANCE_CATEGORY"),
                        bucket.get("USAGE_TYPE"),
                        bucket.get("VALIDITY_PERIOD_DAYS"),
                        bucket.get("BUCKET_UNIT_VALUE"),
                        bucket.get("BUCKET_UNIT_TYPE"),
                        bucket.get("ITERATIVE_BUCKET_YN"),
                        bucket.get("ITERATIVE_COUNTS"),
                        bucket.get("ROLL_OVER_YN"),
                        bucket.get("EXTEND_VALIDITY_YN"),
                        bucket.get("BUCKET_STATUS"),
                        bucket.get("STATUS_DATE"),
                        bucket.get("CREATED_BY"),
                        networkId);
 
                logger.info("Inserted new bucket newBucketId={}", newBucketId);
 
                jdbcTemplate.update(
                        """
                        INSERT INTO CS_BNDL_MT_BNDL_BUCKET_MAP
                        (
                        BUNDLE_ID,
                        BUCKET_ID,
                        NETWORK_ID
                        )
                        VALUES
                        (?,?,?)
                        """,
                        newBundleId,
                        newBucketId,
                        networkId);
 
                logger.debug("Mapped bundle {} -> bucket {}", newBundleId, newBucketId);
            }
 
            jdbcTemplate.update(
                    """
                    INSERT INTO CS_ATP_ACCUMU_BON_DISC_MAP
                    (
                    BUNDLE_OR_DISCOUNT_ID,
                    ATP_ID,
                    PLAN_TYPE,
                    NETWORK_ID
                    )
                    VALUES
                    (?,?,?,?)
                    """,
                    newBundleId,
                    newAtpId,
                    "B",
                    networkId);
 
            logger.debug("Mapped ATP {} -> bundle {}", newAtpId, newBundleId);
 
            List<Map<String, Object>> serviceMapList = jdbcTemplate.queryForList(
                    """
                    SELECT
                    BASIC_SERVICE_ID,
                    DERIVED_SERVICE_ID
                    FROM CS_RAT_SERVICE_ATP_MAP
                    WHERE SERVICE_PACKAGE_ID = ?
                    AND NETWORK_ID = ?
                    """,
                    atpId,
                    networkId);
 
            for (Map<String, Object> row : serviceMapList) {
 
                jdbcTemplate.update(
                        """
                        INSERT INTO CS_RAT_SERVICE_ATP_MAP
                        (
                        NETWORK_ID,
                        SERVICE_PACKAGE_ID,
                        BASIC_SERVICE_ID,
                        DERIVED_SERVICE_ID
                        )
                        VALUES
                        (?,?,?,?)
                        """,
                        networkId,
                        newAtpId,
                        row.get("BASIC_SERVICE_ID"),
                        row.get("DERIVED_SERVICE_ID"));
            }
 
            logger.debug("Service mappings copied for newAtpId={}", newAtpId);
        }
 
        logger.info("ATP clone completed successfully newAtpId={}", newAtpId);
 
        return newAtpId;
    }
}