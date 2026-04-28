//package com.xius.TariffBuilder.UserService;
//
//import java.sql.Date;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.LinkedHashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import com.xius.TariffBuilder.util.JsonStorage;
//
//@Service
//public class TariffApprovalService {
//
//	@Autowired
//	private JdbcTemplate jdbcTemplate;
//
//	@Autowired
//	private JsonStorage jsonStorage;
//
//	@Autowired
//	private ServiceCloneService serviceCloneService;
//
//	@Autowired
//	private BundleService bundleService;
//
//	private static final Logger logger = LoggerFactory.getLogger(TariffApprovalService.class);
//
//	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
//
//	@Transactional(rollbackFor = Exception.class)
//	public Map<String, Object> approve(String tpName) {
//
//		long startTime = System.currentTimeMillis();
//
//		logger.info("Approve request received tpName={}", tpName);
//
//		Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);
//
//		if (json == null) {
//
//			logger.error("Tariff approval failed JSON not found tpName={}", tpName);
//
//			throw new RuntimeException("JSON NOT FOUND");
//		}
//
//		Map<String, Object> data = (Map<String, Object>) json.get("data");
//
//		Long networkId = Long.valueOf(json.get("networkId").toString());
//
//		String username = json.get("username").toString();
//
//		/* REQUEST BODY */
//
//		logger.info("Approve request payload tpName={} networkId={} username={} data={}", tpName, networkId, username,
//				data);
//
//		try {
//
//			/* periodic charge */
//
//			logger.info("Processing periodic charge chargeId={}", data.get("chargeId"));
//
//			insertPeriodicCharge(data, networkId, username);
//
//			/* clone base service */
//
//			logger.info("Cloning base service plan servicePackageId={}", data.get("tariffPlanId"));
//
//			Long newServicePlanId = serviceCloneService.cloneService(networkId,
//					Long.valueOf(data.get("tariffPlanId").toString()), tpName);
//
//			logger.info("Base service cloned newServicePlanId={}", newServicePlanId);
//
//			/* clone ATP */
//
//			Set<Long> newAtpIds = new LinkedHashSet<>();
//
//			List<Map<String, Object>> defaultAtps = (List<Map<String, Object>>) data.get("defaultAtps");
//
//			if (defaultAtps != null) {
//
//				for (Map<String, Object> atp : defaultAtps) {
//
//					Long oldAtpId = Long.valueOf(atp.get("servicePackageId").toString());
//
//					logger.info("Cloning default ATP servicePackageId={}", oldAtpId);
//
//					Long newAtpId = bundleService.cloneAtpData(oldAtpId, networkId, tpName);
//
//					newAtpIds.add(newAtpId);
//
//					logger.info("Default ATP cloned newServicePackageId={}", newAtpId);
//				}
//			}
//
//			List<Map<String, Object>> addAtps = (List<Map<String, Object>>) data.get("additionalAtps");
//
//			if (addAtps != null) {
//
//				for (Map<String, Object> atp : addAtps) {
//
//					Long oldAtpId = Long.valueOf(atp.get("servicePackageId").toString());
//
//					logger.info("Cloning additional ATP servicePackageId={}", oldAtpId);
//
//					Long newAtpId = bundleService.cloneAtpData(oldAtpId, networkId, tpName);
//
//					newAtpIds.add(newAtpId);
//
//					logger.info("Additional ATP cloned newServicePackageId={}", newAtpId);
//				}
//			}
//
//			logger.info("Total ATPs created count={}", newAtpIds.size());
//
//			/* generate tariff id */
//
//			Long tariffId = jdbcTemplate.queryForObject("select SEQ_TARIFF_PACK_ID.nextval from dual", Long.class);
//
//			logger.info("Generated tariff package id tariffId={}", tariffId);
//
//			/* insert tariff package */
//
//			logger.info("Creating tariff package tariffId={} tariffName={}", tariffId, data.get("tariffPackageDesc"));
//
//			jdbcTemplate.update(
//
//					"""
//							insert into CS_RAT_TARIFF_PACKAGE
//							(
//							TARIFF_PACKAGE_ID,
//							TARIFF_PACKAGE_DESC,
//							NETWORK_ID,
//							END_DATE,
//							PUBLICITY_ID,
//							CHARGE_ID,
//							PACKAGE_TYPE,
//							IS_CORPORATE_YN,
//							TARIFF_PACK_CATEGORY
//							)
//							values (?,?,?,?,?,?,?,?,?)
//							""",
//
//					tariffId,
//
//					data.get("tariffPackageDesc"),
//
//					networkId,
//
//					Date.valueOf(LocalDate.parse(data.get("endDate").toString(), formatter)),
//
//					data.get("publicityId"),
//
//					data.get("chargeId"),
//
//					data.get("packageType"),
//
//					convertYN(data.get("isCorporateYn")),
//
//					data.get("tariffPackCategory"));
//
//			logger.info("Tariff package inserted tariffId={}", tariffId);
//
//			/* publicity mapping */
//
//			logger.info("Mapping publicity tariffId={} publicityId={}", tariffId, data.get("publicityId"));
//
//			jdbcTemplate.update(
//
//					"""
//							insert into CS_RAT_TPID_VS_PUBLICITYID
//							(
//							NETWORK_ID,
//							TARIFF_PACKAGE_ID,
//							TARIFF_PACKAGE_DESC,
//							PUBLICITY_ID,
//							RECORD_INSERTED_BY,
//							REC_INSERTED_DATE
//							)
//							values (?,?,?,?,?,sysdate)
//							""",
//
//					networkId,
//
//					tariffId,
//
//					data.get("tariffPackageDesc"),
//
//					data.get("publicityId"),
//
//					username);
//
//			logger.info("Publicity mapping inserted tariffId={}", tariffId);
//
//			/* map service */
//
//			logger.info("Mapping tariff to base service tariffId={} serviceId={}", tariffId, newServicePlanId);
//
//			jdbcTemplate.update(
//
//					"""
//							insert into CS_RAT_TARIFF_SERVICE_PACK_MAP
//							(
//							TARIFF_PACKAGE_ID,
//							SERVICE_PACKAGE_ID,
//							NETWORK_ID,
//							TARIFF_PLAN_TYPE
//							)
//							values (?,?,?,?)
//							""",
//
//					tariffId,
//
//					newServicePlanId,
//
//					networkId,
//
//					"TP");
//
//			logger.info("Base service mapping completed");
//
//			/* map ATP */
//
//			for (Long newAtpId : newAtpIds) {
//
//				logger.info("Mapping tariff to ATP tariffId={} atpId={}", tariffId, newAtpId);
//
//				jdbcTemplate.update(
//
//						"""
//								insert into CS_RAT_TARIFF_SERVICE_PACK_MAP
//								(
//								TARIFF_PACKAGE_ID,
//								SERVICE_PACKAGE_ID,
//								NETWORK_ID,
//								TARIFF_PLAN_TYPE,
//								CHARGE_ID,
//								PRIORITY,
//								SERVICE_DURATION
//								)
//								values (?,?,?,?,?,?,?)
//								""",
//
//						tariffId,
//
//						newAtpId,
//
//						networkId,
//
//						"DATP",
//
//						data.get("chargeId"),
//
//						1,
//
//						30);
//			}
//
//			logger.info("ATP mapping completed tariffId={}", tariffId);
//
//			/* approval status */
//
//			logger.info("Updating approval status tariffId={} status=A", tariffId);
//
//			jdbcTemplate.update(
//
//					"""
//							insert into CS_TARIFF_PACK_AP_REG_STATUS
//							(
//							NETWORK_ID,
//							TARIFF_PACKAGE_ID,
//							TARIFF_PACKAGE_NAME,
//							STATUS
//							)
//							values (?,?,?,?)
//							""",
//
//					networkId,
//
//					tariffId,
//
//					data.get("tariffPackageDesc"),
//
//					"A");
//
//			/* remove json */
//
//			logger.info("Removing TP json tpName={}", tpName);
//
//			removeFromJson(tpName);
//
//			long time = System.currentTimeMillis() - startTime;
//
//			/* RESPONSE BODY */
//
//			Map<String, Object> response = Map.of("tariffPackageId", tariffId);
//
//			logger.info("Approve response payload {}", response);
//
//			logger.info("Tariff created successfully tariffId={} executionTime={}ms", tariffId, time);
//
//			return response;
//
//		} catch (Exception ex) {
//
//			logger.error("Tariff creation failed tpName={} error={}", tpName, ex.getMessage(), ex);
//
//			throw ex;
//		}
//	}
//
//	@Transactional
//	public void reject(String tpName) {
//
//		logger.info("Reject request received tpName={}", tpName);
//
//		Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);
//
//		if (json == null) {
//
//			logger.error("Reject failed JSON not found tpName={}", tpName);
//
//			throw new RuntimeException("JSON NOT FOUND");
//		}
//
//		removeFromJson(tpName);
//
//		logger.info("Tariff rejected successfully tpName={}", tpName);
//	}
//
//	private void insertPeriodicCharge(Map<String, Object> data, Long networkId, String username) {
//
//		String chargeId = data.get("chargeId").toString();
//
//		logger.info("Checking periodic charge chargeId={} networkId={}", chargeId, networkId);
//
//		Integer count = jdbcTemplate.queryForObject(
//
//				"""
//						select count(*)
//						from CS_RAT_PERIODIC_CHARGE_INFO
//						where CHARGE_ID=?
//						and NETWORK_ID=?
//						""",
//
//				Integer.class,
//
//				chargeId,
//
//				networkId);
//
//		if (count > 0) {
//
//			logger.info("Periodic charge already exists chargeId={}", chargeId);
//
//			return;
//		}
//
//		List<Map<String, Object>> atps = (List<Map<String, Object>>) data.get("defaultAtps");
//
//		if (atps == null || atps.isEmpty()) {
//
//			logger.info("Default ATP not available skipping periodic charge creation");
//
//			return;
//		}
//
//		Map<String, Object> atp = atps.get(0);
//
//		logger.info("Creating periodic charge chargeId={}", chargeId);
//
//		jdbcTemplate.update(
//
//				"""
//						insert into CS_RAT_PERIODIC_CHARGE_INFO
//						(
//						CHARGE_ID,
//						CHARGE_DESC,
//						NETWORK_ID,
//						SERVICE_TYPE,
//						RENTAL_TYPE,
//						RENTAL_PERIOD,
//						RENTAL_FEE,
//						RENTAL_FREE_CYCLES,
//						AUTO_RENEWAL,
//						PLAN_EXP_MIDNIGHT_YN,
//						MAX_RENEWAL_COUNT,
//						CREATED_BY
//						)
//						values (?,?,?,?,?,?,?,?,?,?,?,?)
//						""",
//
//				chargeId,
//
//				chargeId,
//
//				networkId,
//
//				data.get("tariffPlanId"),
//
//				atp.get("validity"),
//
//				1,
//
//				atp.get("rental"),
//
//				atp.get("freeCycles"),
//
//				convertYN(atp.get("renewal")),
//
//				convertYN(atp.get("midnightExpiry")),
//
//				atp.get("maxCount"),
//
//				username);
//
//		logger.info("Periodic charge created successfully chargeId={}", chargeId);
//	}
//
//	private String convertYN(Object value) {
//
//		if (value == null)
//			return "N";
//
//		String v = value.toString();
//
//		if (v.equalsIgnoreCase("Y") || v.equalsIgnoreCase("YES") || v.equalsIgnoreCase("true"))
//			return "Y";
//
//		return "N";
//	}
//
//	private void removeFromJson(String tpName) {
//
//		Map<String, Object> json = (Map<String, Object>) jsonStorage.readAll();
//
//		json.remove(tpName);
//
//		jsonStorage.writeAll(json);
//
//		logger.info("TP removed from json storage tpName={}", tpName);
//	}
//}






package com.xius.TariffBuilder.UserService;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xius.TariffBuilder.UserService.BundleService.CloneAtpResult;
import com.xius.TariffBuilder.UserService.ServiceCloneService.CloneServiceResult;
import com.xius.TariffBuilder.util.JsonStorage;

@Service
public class TariffApprovalService {

  private static final Logger logger = LoggerFactory.getLogger(TariffApprovalService.class);

  private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private JsonStorage jsonStorage;

  @Autowired
  private ServiceCloneService serviceCloneService;

  @Autowired
  private BundleService bundleService;

  @Autowired
  private ServiceplanZone servicePlanZone;

  /*
   * =====================================================
   * APPROVE TARIFF
   * =====================================================
   *
   * Required flow:
   *
   * 1. Get oldPlanId from package
   * 2. Get oldPlanZoneId from CS_RAT_SERVICE_PLANS
   * 3. Get oldBucketId from bundle
   * 4. Get oldBucketZoneId from BNDL_MT_BUCKETS
   * 5. Compare both zone ids
   *
   * If same:
   *   - generate one new zone id from plan table
   *   - clone plan using same zone
   *   - clone bucket using same zone
   *   - clone zone data old -> new
   *
   * If different:
   *   - generate new plan zone id from plan table
   *   - generate new bucket zone id from bucket table
   *   - clone plan using new plan zone
   *   - clone bucket using new bucket zone
   *   - clone both zone data separately
   */
  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> approve(String tpName) {

      long startTime = System.currentTimeMillis();

      logger.info("Approve request received tpName={}", tpName);

      Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);

      if (json == null) {
          throw new RuntimeException("JSON NOT FOUND");
      }

      Map<String, Object> data = (Map<String, Object>) json.get("data");

      Long networkId = Long.valueOf(json.get("networkId").toString());
      String username = json.get("username").toString();

      logger.info("Approve request payload tpName={} networkId={} username={}",
              tpName, networkId, username);

      try {
          /*
           * =====================================================
           * STEP 1: Get old plan and old plan zone
           * =====================================================
           */
          Long oldServicePackageId = Long.valueOf(data.get("tariffPlanId").toString());

          Long oldPlanId = serviceCloneService.getOldPlanId(networkId, oldServicePackageId);

          if (oldPlanId == null) {
              throw new RuntimeException("Old plan not found for packageId=" + oldServicePackageId);
          }

          Long oldPlanZoneId = serviceCloneService.getPlanZoneId(oldPlanId);

          /*
           * =====================================================
           * STEP 2: Get old bucket and old bucket zone
           * =====================================================
           */
          List<Map<String, Object>> defaultAtps =
                  (List<Map<String, Object>>) data.get("defaultAtps");

          if (defaultAtps == null || defaultAtps.isEmpty()) {
              throw new RuntimeException("Default ATP is required");
          }

          Long firstOldAtpId = Long.valueOf(defaultAtps.get(0).get("servicePackageId").toString());

          String oldBucketId = bundleService.getOldBucketId(firstOldAtpId, networkId);

          if (oldBucketId == null) {
              throw new RuntimeException("Old bucket not found for ATP=" + firstOldAtpId);
          }

          Long oldBucketZoneId = bundleService.getBucketZoneId(oldBucketId);

          logger.info("Old mapping oldPlanId={} oldPlanZoneId={} oldBucketId={} oldBucketZoneId={}",
                  oldPlanId, oldPlanZoneId, oldBucketId, oldBucketZoneId);

          /*
           * =====================================================
           * STEP 3: Decide new plan zone and new bucket zone
           * =====================================================
           */
          Long newPlanZoneId;
          Long newBucketZoneId;

          if (oldPlanZoneId != null && oldPlanZoneId.equals(oldBucketZoneId)) {

              Long newZoneId = servicePlanZone.generateNewZoneId();

              newPlanZoneId = newZoneId;
              newBucketZoneId = newZoneId;

              servicePlanZone.cloneZoneIfExists(
                      oldPlanZoneId,
                      newZoneId,
                      networkId,
                      tpName
              );

          } else {

              newPlanZoneId = servicePlanZone.generateNewZoneId();

              servicePlanZone.cloneZoneIfExists(
                      oldPlanZoneId,
                      newPlanZoneId,
                      networkId,
                      tpName
              );

              newBucketZoneId = servicePlanZone.generateNewZoneId();

              servicePlanZone.cloneZoneIfExists(
                      oldBucketZoneId,
                      newBucketZoneId,
                      networkId,
                      tpName
              );
          }
          /*
           * =====================================================
           * STEP 4: Clone service package and plan using newPlanZoneId
           * =====================================================
           */
          CloneServiceResult serviceResult = serviceCloneService.cloneService(
                  networkId,
                  oldServicePackageId,
                  tpName,
                  newPlanZoneId
          );

          Long newServicePackageId = serviceResult.getNewPackageId();
          Long newServicePlanId = serviceResult.getNewPlanId();

          logger.info("Base service cloned newServicePackageId={} newServicePlanId={}",
                  newServicePackageId, newServicePlanId);

          /*
           * =====================================================
           * STEP 5: Clone ATP, bundle and bucket using newBucketZoneId
           * =====================================================
           */

          List<Long> defaultAtpIds = new ArrayList<>();
          List<Long> allowedAtpIds = new ArrayList<>();
          List<Long> newAtpIds = new ArrayList<>();

          if (defaultAtps != null) {
          for (Map<String, Object> atp : defaultAtps) {

              Long oldAtpId = Long.valueOf(atp.get("servicePackageId").toString());

              CloneAtpResult atpResult = bundleService.cloneAtpData(
                      oldAtpId,
                      networkId,
                      tpName,
                      newBucketZoneId
              );

              defaultAtpIds.add(atpResult.getNewAtpId());
              newAtpIds.add(atpResult.getNewAtpId());

              logger.info("Default ATP cloned oldAtpId={} newAtpId={}",
                      oldAtpId, atpResult.getNewAtpId());
          }
          }
          logger.info("After default ATPs defaultAtpIds={}", defaultAtpIds);
          
          List<Map<String, Object>> addAtps =
                  (List<Map<String, Object>>) data.get("allowedAtps");

          logger.info("Allowed ATP count={}", addAtps != null ? addAtps.size() : 0);

          if (addAtps != null) {

              for (Map<String, Object> atp : addAtps) {

                  Long oldAtpId = Long.valueOf(atp.get("servicePackageId").toString());

                  CloneAtpResult atpResult = bundleService.cloneAtpData(
                          oldAtpId,
                          networkId,
                          tpName,
                          newBucketZoneId
                  );

                  allowedAtpIds.add(atpResult.getNewAtpId());
                  newAtpIds.add(atpResult.getNewAtpId());

                  logger.info("Allowed ATP cloned oldAtpId={} newAtpId={}",
                          oldAtpId, atpResult.getNewAtpId());
              }
          }

          logger.info("Final defaultAtpIds for DATP mapping={}", defaultAtpIds);
          logger.info("Final allowedAtpIds for AATP mapping={}", allowedAtpIds);
          /*
           * =====================================================
           * STEP 6: Periodic charge
           * =====================================================
           */
          insertPeriodicCharge(data, networkId, username);

          /*
           * =====================================================
           * STEP 7: Create tariff package
           * =====================================================
           */
          Long tariffId = jdbcTemplate.queryForObject(
                  "select SEQ_TARIFF_PACK_ID.nextval from dual",
                  Long.class
          );

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
                  Date.valueOf(LocalDate.parse(data.get("endDate").toString(), formatter)),
                  data.get("publicityId"),
                  data.get("chargeId"),
                  data.get("packageType"),
                  convertYN(data.get("isCorporateYn")),
                  data.get("tariffPackCategory")
          );

          /*
           * =====================================================
           * STEP 8: Publicity mapping
           * =====================================================
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
                  username
          );

          /*
           * =====================================================
           * STEP 9: Map tariff to base service package
           * IMPORTANT:
           * This column is SERVICE_PACKAGE_ID,
           * so use newServicePackageId, not newServicePlanId.
           * =====================================================
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
                  newServicePackageId,
                  networkId,
                  "TP"
          );

          /*
           * =====================================================
           * STEP 10: Map tariff to ATPs
           * =====================================================
           */
          
          

          // Default ATPs -> DATP
          for (Long atpId : defaultAtpIds) {

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
                      atpId,
                      networkId,
                      "DATP",
                      data.get("chargeId"),
                      1,
                      30
              );
          }

          // Allowed ATPs -> AATP
          for (Long atpId : allowedAtpIds) {

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
                      atpId,
                      networkId,
                      "AATP",
                      data.get("chargeId"),
                      1,
                      30
              );
          }
          /*
           * =====================================================
           * STEP 11: Approval status
           * =====================================================
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
                  "A"
          );

          removeFromJson(tpName);

          long time = System.currentTimeMillis() - startTime;

          logger.info("Tariff approved tariffId={} executionTime={}ms", tariffId, time);

          return Map.of(
                  "tariffPackageId", tariffId,
                  "newServicePackageId", newServicePackageId,
                  "newServicePlanId", newServicePlanId,
                  "newAtpIds", newAtpIds,
                  "newPlanZoneId", newPlanZoneId,
                  "newBucketZoneId", newBucketZoneId
          );

      } catch (Exception ex) {
          logger.error("Tariff creation failed tpName={} error={}", tpName, ex.getMessage(), ex);
          throw ex;
      }
  }

  @Transactional
  public void reject(String tpName) {

      Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);

      if (json == null) {
          throw new RuntimeException("JSON NOT FOUND");
      }

      removeFromJson(tpName);

      logger.info("Tariff rejected successfully tpName={}", tpName);
  }

  private void insertPeriodicCharge(Map<String, Object> data, Long networkId, String username) {

      String chargeId = data.get("chargeId").toString();

      Integer count = jdbcTemplate.queryForObject(
              """
              select count(*)
              from CS_RAT_PERIODIC_CHARGE_INFO
              where CHARGE_ID = ?
              and NETWORK_ID = ?
              """,
              Integer.class,
              chargeId,
              networkId
      );

      if (count != null && count > 0) {
          logger.info("Periodic charge already exists chargeId={}", chargeId);
          return;
      }

      List<Map<String, Object>> atps =
              (List<Map<String, Object>>) data.get("defaultAtps");

      if (atps == null || atps.isEmpty()) {
          logger.info("Default ATP not available. Skipping periodic charge.");
          return;
      }

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
              username
      );
  }

  private String convertYN(Object value) {

      if (value == null) {
          return "N";
      }

      String v = value.toString();

      if (v.equalsIgnoreCase("Y") ||
          v.equalsIgnoreCase("YES") ||
          v.equalsIgnoreCase("TRUE")) {
          return "Y";
      }

      return "N";
  }

  private void removeFromJson(String tpName) {

      Map<String, Object> json =
              (Map<String, Object>) jsonStorage.readAll();

      json.remove(tpName);

      jsonStorage.writeAll(json);

      logger.info("TP removed from json storage tpName={}", tpName);
  }
}