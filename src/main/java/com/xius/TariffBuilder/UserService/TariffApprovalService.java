package com.xius.TariffBuilder.UserService;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xius.TariffBuilder.util.JsonStorage;

@Service
public class TariffApprovalService {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private JsonStorage jsonStorage;

        @Autowired
        private ServiceCloneService serviceCloneService;

        @Autowired
        private BundleService bundleService;

        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

        @Transactional(rollbackFor = Exception.class)
        public Map<String, Object> approve(String tpName) {

                Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);

                if (json == null)
                        throw new RuntimeException("JSON NOT FOUND");

                Map<String, Object> data = (Map<String, Object>) json.get("data");

                Long networkId = Long.valueOf(json.get("networkId").toString());

                String username = json.get("username").toString();

                /*
                 * STEP 1
                 * periodic charge
                 */
                insertPeriodicCharge(data, networkId, username);

                /*
                 * STEP 2
                 * clone service plan
                 */
                Long newServicePlanId = serviceCloneService.cloneService(
                                networkId,
                                Long.valueOf(
                                                data.get("tariffPlanId").toString()),
                                tpName);
                /*
                 * STEP 3
                 * clone ATPs and capture newly created ATP IDs
                 */
                Set<Long> newAtpIds = new LinkedHashSet<>();

                List<Map<String, Object>> defaultAtps = (List<Map<String, Object>>) data.get("defaultAtps");

                if (defaultAtps != null) {

                        for (Map<String, Object> atp : defaultAtps) {

                                Long oldAtpId = Long.valueOf(
                                                atp.get("servicePackageId").toString());

                                Long newAtpId = bundleService.cloneAtpData(
                                                oldAtpId,
                                                networkId,
                                                tpName);

                                newAtpIds.add(newAtpId);
                        }
                }

                List<Map<String, Object>> addAtps = (List<Map<String, Object>>) data.get("additionalAtps");

                if (addAtps != null) {

                        for (Map<String, Object> atp : addAtps) {

                                Long oldAtpId = Long.valueOf(
                                                atp.get("servicePackageId").toString());

                                Long newAtpId = bundleService.cloneAtpData(
                                                oldAtpId,
                                                networkId,
                                                tpName);

                                newAtpIds.add(newAtpId);
                        }
                }
                /*
                 * STEP 4
                 * generate tariff package id
                 */
                Long tariffId = jdbcTemplate.queryForObject(
                                "select SEQ_TARIFF_PACK_ID.nextval from dual",
                                Long.class);
                /*
                 * STEP 5
                 * insert tariff package
                 */
                jdbcTemplate.update(

                                """
                                                insert into CS_RAT_TARIFF_PACKAGE
                                                (
                                                TARIFF_PACKAGE_ID,
                                                TARIFF_PACKAGE_DESC,
                                                NETWORK_ID,
                                                END_DATE,
                                                PUBLICITY_ID,
                                                CHARGE_ID,
                                                PACKAGE_TYPE,
                                                IS_CORPORATE_YN,
                                                TARIFF_PACK_CATEGORY
                                                )
                                                values (?,?,?,?,?,?,?,?,?)
                                                """,

                                tariffId,
                                data.get("tariffPackageDesc"),
                                networkId,

                                Date.valueOf(
                                                LocalDate.parse(
                                                                data.get("endDate").toString(),
                                                                formatter)),

                                data.get("publicityId"),
                                data.get("chargeId"),
                                data.get("packageType"),
                                convertYN(data.get("isCorporateYn")),
                                data.get("tariffPackCategory"));
                /*
                 * STEP 6
                 * publicity mapping
                 */
                jdbcTemplate.update(

                                """
                                                insert into CS_RAT_TPID_VS_PUBLICITYID
                                                (
                                                NETWORK_ID,
                                                TARIFF_PACKAGE_ID,
                                                TARIFF_PACKAGE_DESC,
                                                PUBLICITY_ID,
                                                RECORD_INSERTED_BY,
                                                REC_INSERTED_DATE
                                                )
                                                values (?,?,?,?,?,sysdate)
                                                """,
                                networkId,
                                tariffId,
                                data.get("tariffPackageDesc"),
                                data.get("publicityId"),
                                username);
                /*
                 * STEP 7
                 * map TP → cloned service plan
                 */
                jdbcTemplate.update(

                                """
                                                insert into CS_RAT_TARIFF_SERVICE_PACK_MAP
                                                (
                                                TARIFF_PACKAGE_ID,
                                                SERVICE_PACKAGE_ID,
                                                NETWORK_ID,
                                                TARIFF_PLAN_TYPE
                                                )
                                                values (?,?,?,?)
                                                """,

                                tariffId,
                                newServicePlanId,
                                networkId,
                                "TP");
                /*
                 * STEP 8
                 * map TP → cloned ATPs
                 * using newly created ATP IDs
                 */
                for (Long newAtpId : newAtpIds) {

                        jdbcTemplate.update(

                                        """
                                                        insert into CS_RAT_TARIFF_SERVICE_PACK_MAP
                                                        (
                                                        TARIFF_PACKAGE_ID,
                                                        SERVICE_PACKAGE_ID,
                                                        NETWORK_ID,
                                                        TARIFF_PLAN_TYPE,
                                                        CHARGE_ID,
                                                        PRIORITY,
                                                        SERVICE_DURATION
                                                        )
                                                        values (?,?,?,?,?,?,?)
                                                        """,
                                        tariffId,
                                        newAtpId,
                                        networkId,
                                        "DATP",
                                        data.get("chargeId"),
                                        1,
                                        30);
                }
                /*
                 * STEP 9
                 * approval status
                 */
                jdbcTemplate.update(

                                """
                                                insert into CS_TARIFF_PACK_AP_REG_STATUS
                                                (
                                                NETWORK_ID,
                                                TARIFF_PACKAGE_ID,
                                                TARIFF_PACKAGE_NAME,
                                                STATUS
                                                )
                                                values (?,?,?,?)
                                                """,

                                networkId,
                                tariffId,
                                data.get("tariffPackageDesc"),
                                "A");

                /*
                 * STEP 10
                 * remove json after success
                 */
                removeFromJson(tpName);

                return Map.of(
                                "tariffPackageId",
                                tariffId);
        }

        @Transactional
        public void reject(String tpName) {

                Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);

                if (json == null) {
                        throw new RuntimeException("JSON NOT FOUND");
                }

                /* only remove JSON */

                removeFromJson(tpName);
        }

        private void insertPeriodicCharge(
                        Map<String, Object> data,
                        Long networkId,
                        String username) {

                String chargeId = data.get("chargeId").toString();

                Integer count = jdbcTemplate.queryForObject(

                                """
                                                select count(*)
                                                from CS_RAT_PERIODIC_CHARGE_INFO
                                                where CHARGE_ID=?
                                                and NETWORK_ID=?
                                                """,

                                Integer.class,
                                chargeId,
                                networkId);

                if (count > 0)
                        return;

                List<Map<String, Object>> atps = (List<Map<String, Object>>) data.get("defaultAtps");

                Map<String, Object> atp = atps.get(0);

                jdbcTemplate.update(

                                """
                                                insert into CS_RAT_PERIODIC_CHARGE_INFO
                                                (
                                                CHARGE_ID,
                                                CHARGE_DESC,
                                                NETWORK_ID,
                                                SERVICE_TYPE,
                                                RENTAL_TYPE,
                                                RENTAL_PERIOD,
                                                RENTAL_FEE,
                                                RENTAL_FREE_CYCLES,
                                                AUTO_RENEWAL,
                                                PLAN_EXP_MIDNIGHT_YN,
                                                MAX_RENEWAL_COUNT,
                                                CREATED_BY
                                                )
                                                values (?,?,?,?,?,?,?,?,?,?,?,?)
                                                """,

                                chargeId,
                                chargeId,
                                networkId,

                                data.get("tariffPlanId"),

                                atp.get("validity"),
                                1,
                                atp.get("rental"),
                                atp.get("freeCycles"),

                                convertYN(atp.get("renewal")),
                                convertYN(atp.get("midnightExpiry")),

                                atp.get("maxCount"),
                                username);
        }

        private String convertYN(Object value) {

                if (value == null)
                        return "N";

                String v = value.toString();

                if (v.equalsIgnoreCase("Y")
                                || v.equalsIgnoreCase("YES")
                                || v.equalsIgnoreCase("true"))

                        return "Y";

                return "N";
        }

        private void removeFromJson(String tpName) {

                Map<String, Object> json = (Map<String, Object>) jsonStorage.readAll();

                json.remove(tpName);

                jsonStorage.writeAll(json);
        }

}