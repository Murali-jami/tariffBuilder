package com.xius.TariffBuilder.UserService;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger logger = LoggerFactory.getLogger(TariffApprovalService.class);

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> approve(String tpName) {

		long startTime = System.currentTimeMillis();

		logger.info("Approve request received tpName={}", tpName);

		Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);

		if (json == null) {

			logger.error("Tariff approval failed JSON not found tpName={}", tpName);

			throw new RuntimeException("JSON NOT FOUND");
		}

		Map<String, Object> data = (Map<String, Object>) json.get("data");

		Long networkId = Long.valueOf(json.get("networkId").toString());

		String username = json.get("username").toString();

		/* REQUEST BODY */

		logger.info("Approve request payload tpName={} networkId={} username={} data={}", tpName, networkId, username,
				data);

		try {

			/* periodic charge */

			logger.info("Processing periodic charge chargeId={}", data.get("chargeId"));

			insertPeriodicCharge(data, networkId, username);

			/* clone base service */

			logger.info("Cloning base service plan servicePackageId={}", data.get("tariffPlanId"));

			Long newServicePlanId = serviceCloneService.cloneService(networkId,
					Long.valueOf(data.get("tariffPlanId").toString()), tpName);

			logger.info("Base service cloned newServicePlanId={}", newServicePlanId);

			/* clone ATP */

			Set<Long> newAtpIds = new LinkedHashSet<>();

			List<Map<String, Object>> defaultAtps = (List<Map<String, Object>>) data.get("defaultAtps");

			if (defaultAtps != null) {

				for (Map<String, Object> atp : defaultAtps) {

					Long oldAtpId = Long.valueOf(atp.get("servicePackageId").toString());

					logger.info("Cloning default ATP servicePackageId={}", oldAtpId);

					Long newAtpId = bundleService.cloneAtpData(oldAtpId, networkId, tpName);

					newAtpIds.add(newAtpId);

					logger.info("Default ATP cloned newServicePackageId={}", newAtpId);
				}
			}

			List<Map<String, Object>> addAtps = (List<Map<String, Object>>) data.get("additionalAtps");

			if (addAtps != null) {

				for (Map<String, Object> atp : addAtps) {

					Long oldAtpId = Long.valueOf(atp.get("servicePackageId").toString());

					logger.info("Cloning additional ATP servicePackageId={}", oldAtpId);

					Long newAtpId = bundleService.cloneAtpData(oldAtpId, networkId, tpName);

					newAtpIds.add(newAtpId);

					logger.info("Additional ATP cloned newServicePackageId={}", newAtpId);
				}
			}

			logger.info("Total ATPs created count={}", newAtpIds.size());

			/* generate tariff id */

			Long tariffId = jdbcTemplate.queryForObject("select SEQ_TARIFF_PACK_ID.nextval from dual", Long.class);

			logger.info("Generated tariff package id tariffId={}", tariffId);

			/* insert tariff package */

			logger.info("Creating tariff package tariffId={} tariffName={}", tariffId, data.get("tariffPackageDesc"));

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

					data.get("tariffPackCategory"));

			logger.info("Tariff package inserted tariffId={}", tariffId);

			/* publicity mapping */

			logger.info("Mapping publicity tariffId={} publicityId={}", tariffId, data.get("publicityId"));

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

			logger.info("Publicity mapping inserted tariffId={}", tariffId);

			/* map service */

			logger.info("Mapping tariff to base service tariffId={} serviceId={}", tariffId, newServicePlanId);

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

			logger.info("Base service mapping completed");

			/* map ATP */

			for (Long newAtpId : newAtpIds) {

				logger.info("Mapping tariff to ATP tariffId={} atpId={}", tariffId, newAtpId);

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

			logger.info("ATP mapping completed tariffId={}", tariffId);

			/* approval status */

			logger.info("Updating approval status tariffId={} status=A", tariffId);

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

			/* remove json */

			logger.info("Removing TP json tpName={}", tpName);

			removeFromJson(tpName);

			long time = System.currentTimeMillis() - startTime;

			/* RESPONSE BODY */

			Map<String, Object> response = Map.of("tariffPackageId", tariffId);

			logger.info("Approve response payload {}", response);

			logger.info("Tariff created successfully tariffId={} executionTime={}ms", tariffId, time);

			return response;

		} catch (Exception ex) {

			logger.error("Tariff creation failed tpName={} error={}", tpName, ex.getMessage(), ex);

			throw ex;
		}
	}

	@Transactional
	public void reject(String tpName) {

		logger.info("Reject request received tpName={}", tpName);

		Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);

		if (json == null) {

			logger.error("Reject failed JSON not found tpName={}", tpName);

			throw new RuntimeException("JSON NOT FOUND");
		}

		removeFromJson(tpName);

		logger.info("Tariff rejected successfully tpName={}", tpName);
	}

	private void insertPeriodicCharge(Map<String, Object> data, Long networkId, String username) {

		String chargeId = data.get("chargeId").toString();

		logger.info("Checking periodic charge chargeId={} networkId={}", chargeId, networkId);

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

		if (count > 0) {

			logger.info("Periodic charge already exists chargeId={}", chargeId);

			return;
		}

		List<Map<String, Object>> atps = (List<Map<String, Object>>) data.get("defaultAtps");

		if (atps == null || atps.isEmpty()) {

			logger.info("Default ATP not available skipping periodic charge creation");

			return;
		}

		Map<String, Object> atp = atps.get(0);

		logger.info("Creating periodic charge chargeId={}", chargeId);

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

		logger.info("Periodic charge created successfully chargeId={}", chargeId);
	}

	private String convertYN(Object value) {

		if (value == null)
			return "N";

		String v = value.toString();

		if (v.equalsIgnoreCase("Y") || v.equalsIgnoreCase("YES") || v.equalsIgnoreCase("true"))
			return "Y";

		return "N";
	}

	private void removeFromJson(String tpName) {

		Map<String, Object> json = (Map<String, Object>) jsonStorage.readAll();

		json.remove(tpName);

		jsonStorage.writeAll(json);

		logger.info("TP removed from json storage tpName={}", tpName);
	}
}