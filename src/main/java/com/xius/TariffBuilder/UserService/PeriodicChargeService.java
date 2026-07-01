// package com.xius.TariffBuilder.UserService;

// import java.util.ArrayList;
// import java.util.List;
// import java.util.Map;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Qualifier;
// import org.springframework.jdbc.core.JdbcTemplate;
// import org.springframework.stereotype.Service;


// @Service
// public class PeriodicChargeService {

//     private static final Logger logger = LoggerFactory.getLogger(PeriodicChargeService.class);

// 	@Qualifier("oracleJdbcTemplate")
// 	private final JdbcTemplate jdbcTemplate;

//        String query = """
//                 SELECT CHARGE_ID,
//                        CHARGE_DESC,
//                        NETWORK_ID,
//                        RENTAL_PERIOD,
//                        RENTAL_FEE
//                 FROM CS_RAT_PERIODIC_CHARGE_INFO
//                 WHERE NETWORK_ID = ?
//                   AND NOT REGEXP_LIKE(
//                         UPPER(CHARGE_ID),
//                         '(_PR[0-9]+)|(_ATP[0-9]+)|(_TP[0-9]+)|(_CL[0-9]+)'
//                   )
//                 ORDER BY CHARGE_ID
//             """;

//        String singleChargeQuery = """
//                 SELECT CHARGE_ID,
//                        CHARGE_DESC,
//                        NETWORK_ID,
//                        RENTAL_PERIOD,
//                        RENTAL_FEE
//                 FROM CS_RAT_PERIODIC_CHARGE_INFO
//                 WHERE NETWORK_ID = ?
//                   AND CHARGE_ID = ?
//             """;

//     PeriodicChargeService(JdbcTemplate jdbcTemplate) {
//         this.jdbcTemplate = jdbcTemplate;
//     }


//     public List<Map<String, Object>> getPeriodicCharges(Long networkId) {
//         logger.info("Fetching periodic charges for networkId={}", networkId);
//         List<Map<String, Object>> charges = jdbcTemplate.queryForList(query, networkId);
//         if (charges.isEmpty()) {
//             logger.warn("No periodic charges found for networkId={}", networkId);
//             return List.of();
//         }
//         logger.info("Fetched {} periodic charges for networkId={}", charges.size(), networkId);
//         return charges; // Returning all fetched charges
//     }

//     // Same as getPeriodicCharges, but additionally guarantees that
//     // currentChargeId (the charge already assigned to the package being
//     // edited/modified) is present in the list — even if it's normally
//     // hidden by the clone-suffix filter above. Used when loading an
//     // existing package into the builder for edit/update/clone-modify.
//     public List<Map<String, Object>> getPeriodicCharges(Long networkId, String currentChargeId) {
//         List<Map<String, Object>> charges = new ArrayList<>(getPeriodicCharges(networkId));

//         if (currentChargeId == null || currentChargeId.isBlank()) {
//             return charges;
//         }

//         boolean alreadyPresent = charges.stream()
//                 .anyMatch(c -> currentChargeId.equalsIgnoreCase(String.valueOf(c.get("CHARGE_ID"))));

//         if (!alreadyPresent) {
//             logger.info("currentChargeId={} not in filtered list for networkId={}, fetching separately",
//                     currentChargeId, networkId);
//             List<Map<String, Object>> current =
//                     jdbcTemplate.queryForList(singleChargeQuery, networkId, currentChargeId);
//             charges.addAll(current);
//         }

//         return charges;
//     }
// }