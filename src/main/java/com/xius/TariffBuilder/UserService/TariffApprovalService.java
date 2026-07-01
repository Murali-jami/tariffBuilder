package com.xius.TariffBuilder.UserService;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.xius.TariffBuilder.Dto.TariffPackageDetails;
import com.xius.TariffBuilder.Entity.SaveConfigDao;
import com.xius.TariffBuilder.UserService.BundleService.CloneAtpResult;
import com.xius.TariffBuilder.UserService.ServiceCloneService.CloneServiceResult;
import com.xius.TariffBuilder.exception.TariffInsertException;
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

	@Autowired
	private SaveConfigDao saveConfigDao;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// =====================================================
	// APPROVE TARIFF
	// =====================================================
	public Map<String, Object> approve(String tpName) {

		long startTime = System.currentTimeMillis();
		logger.info("Approve request received tpName={}", tpName);

		Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);
		if (json == null) {
			Map<String, Object> err = new HashMap<>();
			err.put("status", "error");
			err.put("message", "JSON NOT FOUND for tpName=" + tpName);
			return err;
		}

		Map<String, Object> data = (Map<String, Object>) json.get("data");
		Long networkId = Long.valueOf(json.get("networkId").toString());
		Object usernameRaw = json.get("username");
		String username = (usernameRaw != null) ? usernameRaw.toString() : "";

		logger.info("Approve payload tpName={} networkId={} username={}", tpName, networkId, username);

		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		TransactionStatus status = transactionManager.getTransaction(def);

		try {
			Map<String, Object> result = executeTariffCreation(data, tpName, networkId, username);
			transactionManager.commit(status);

			// Save to approved-tariffs.json before removing from pending
			Map<String, Object> approvedEntry = new LinkedHashMap<>(json);
			approvedEntry.put("approvedOn", java.time.LocalDateTime.now().toString());
			approvedEntry.put("tariffPackageId", result.get("tariffPackageId"));
			jsonStorage.storeApproved(tpName, approvedEntry);

			removeFromJson(tpName);
			result.put("status", "success");
			long time = System.currentTimeMillis() - startTime;
			logger.info("Tariff approved tpName={} executionTime={}ms", tpName, time);
			return result;
		} catch (TariffInsertException tie) {
			transactionManager.rollback(status);
			logger.error("TariffInsertException during approve tpName={}", tpName, tie);
			throw tie; // Let GlobalExceptionHandler handle the response
		} catch (Exception ex) {
			transactionManager.rollback(status);
			logger.error("Approve failed tpName={}", tpName, ex);
			throw new RuntimeException(ex); // Let GlobalExceptionHandler handle the response
		}
	}

	// =====================================================
	// CLONE TARIFF
	// =====================================================
	public Map<String, Object> clone(Map<String, Object> requestBody) {

		long startTime = System.currentTimeMillis();

		String originalTpName = requestBody.get("tpName").toString();
		Long networkId = Long.valueOf(requestBody.get("networkId").toString());
		Object usernameRaw = requestBody.get("username");
		String username = (usernameRaw != null) ? usernameRaw.toString() : "";
		String cloneMode = requestBody.containsKey("cloneMode") ? requestBody.get("cloneMode").toString() : "direct";

		logger.info("Clone request received originalTpName={} networkId={} username={} cloneMode={}", originalTpName,
				networkId, username, cloneMode);

		// Validate required fields up-front — gives clear error instead of NPE/undefined
		if (requestBody.get("tpName") == null) {
			return Map.of("status", "error", "message", "Missing required field: tpName");
		}
		if (requestBody.get("networkId") == null) {
			return Map.of("status", "error", "message", "Missing required field: networkId");
		}
		Map<String, Object> originalData = (Map<String, Object>) requestBody.get("data");
		if (originalData == null) {
			return Map.of("status", "error", "message", "Missing required field: data");
		}

		String clonedTpName = null;
		String clonedPublicityId = null;

		try {
			if ("modify".equals(cloneMode) && requestBody.containsKey("overrideTpName")
					&& requestBody.containsKey("overridePublicityId")) {
				String overrideTpName = requestBody.get("overrideTpName").toString();
				String overridePublicityId = requestBody.get("overridePublicityId").toString();
				boolean nameUnchanged = overrideTpName.equals(originalTpName);
				if (nameUnchanged) {
					String rootTpName = stripCloneSuffix(originalTpName);
					int cloneNumber = resolveNextCloneNumber(rootTpName, networkId);
					String cloneSuffix = "_CL" + cloneNumber;
					clonedTpName = rootTpName + cloneSuffix;
					clonedPublicityId = stripCloneSuffix(overridePublicityId) + cloneSuffix;
					logger.info(
							"Clone modify mode (name unchanged): rootTpName={} cloneSuffix={} clonedTpName={} clonedPublicityId={}",
							rootTpName, cloneSuffix, clonedTpName, clonedPublicityId);
				} else {
					// B1 — user typed a new name: use it as-is
					clonedTpName = overrideTpName;
					clonedPublicityId = overridePublicityId;
					logger.info("Clone modify mode (name changed): clonedTpName={} clonedPublicityId={}",
							clonedTpName, clonedPublicityId);
				}
			} else {
				String rootTpName = stripCloneSuffix(originalTpName);
				int cloneNumber = resolveNextCloneNumber(rootTpName, networkId);
				String cloneSuffix = "_CL" + cloneNumber;
				clonedTpName = rootTpName + cloneSuffix;
				String originalPublicityId = originalData.get("publicityId").toString();
				clonedPublicityId = stripCloneSuffix(originalPublicityId) + cloneSuffix;
				logger.info(
						"Clone direct mode: originalTpName={} rootTpName={} cloneSuffix={} clonedTpName={} clonedPublicityId={}",
						originalTpName, rootTpName, cloneSuffix, clonedTpName, clonedPublicityId);
			}

		} catch (Exception ex) {
			logger.error("Clone name resolution failed originalTpName={} error={}", originalTpName, ex.getMessage(),
					ex);
			Map<String, Object> err = new LinkedHashMap<>();
			err.put("status", "error");
			err.put("message", "Clone name resolution failed: "
					+ (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
			return err;
		}

		// Guard: if name resolution produced null (should not happen after above),
		// return error
		if (clonedTpName == null) {
			logger.error("clonedTpName is null after name resolution — originalTpName={}", originalTpName);
			return Map.of("status", "error", "message",
					"Clone failed: could not resolve a valid clone name for " + originalTpName);
		}

		// Deep-copy and mutate the data map
		Map<String, Object> clonedData = new HashMap<>(originalData);
		clonedData.put("publicityId", clonedPublicityId);
		clonedData.put("tariffPackageDesc", clonedTpName);
		// chargeIds for ATPs will be regenerated server-side (no top-level chargeId)

		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		TransactionStatus status = transactionManager.getTransaction(def);

		try {
			Map<String, Object> result = executeTariffCreation(clonedData, clonedTpName, networkId, username);
			transactionManager.commit(status);
			result.put("status", "success");
			result.put("clonedTpName", clonedTpName);
			result.put("clonedPublicityId", clonedPublicityId);
			long time = System.currentTimeMillis() - startTime;
			logger.info("Tariff cloned clonedTpName={} executionTime={}ms", clonedTpName, time);
			return result;
		} catch (TariffInsertException tie) {
			transactionManager.rollback(status);
			logger.error("TariffInsertException during clone clonedTpName={}", clonedTpName, tie);
			Map<String, Object> err = new LinkedHashMap<>();
			err.put("status", "error");
			err.put("message", "Error inserting into " + tie.getFailedTable() + " at " + tie.getStep() + ": "
					+ (tie.getCause() != null ? tie.getCause().getMessage() : tie.getMessage()));
			err.put("failedStep", tie.getStep());
			err.put("failedTable", tie.getFailedTable());
			return err;
		} catch (Exception ex) {
			transactionManager.rollback(status);
			logger.error("Clone failed clonedTpName={}", clonedTpName, ex);
			Map<String, Object> err = new HashMap<>();
			err.put("status", "error");
			err.put("message", ex.getMessage() != null ? ex.getMessage() : "Unexpected error during clone");
			return err;
		}
	}

	public Map<String, Object> validateClone(Long networkId, String tpName, String publicityId) {
		Map<String, Object> response = new LinkedHashMap<>();
		if (saveConfigDao.checkTariffExists(networkId, tpName)) {
			response.put("status", "error");
			response.put("message", "Tariff Package already exists in DB: " + tpName);
			return response;
		}
		if (saveConfigDao.checkPublicityExists(networkId, publicityId)) {
			response.put("status", "error");
			response.put("message", "Publicity ID already mapped in DB: " + publicityId);
			return response;
		}
		response.put("status", "success");
		response.put("message", "Validation passed");
		return response;
	}

	// EXTRACT CLONE LABEL
	private String extractCloneLabel(String tpName) {
		if (tpName == null)
			return tpName;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("_(CL\\d+)$").matcher(tpName);
		return m.find() ? m.group(1) : tpName;
	}

	// STRIP CLONE SUFFIX (remove trailing _CL<number>, _TP<number>, or _ATP<number>)
	private String stripCloneSuffix(String name) {
		if (name == null)
			return null;
		return name.replaceAll("_(CL|TP|ATP)\\d+$", "");
	}

	// =====================================================
	// RESOLVE NEXT CLONE NUMBER
	// =====================================================
	private int resolveNextCloneNumber(String originalTpName, Long networkId) {
		List<String> existingDescs = jdbcTemplate.queryForList("""
				select TARIFF_PACKAGE_DESC
				from CS_RAT_TARIFF_PACKAGE
				where NETWORK_ID = ?
				and TARIFF_PACKAGE_DESC like ?
				""", String.class, networkId, originalTpName + "_CL%");

		String prefix = originalTpName + "_CL";
		int max = 0;

		for (String desc : existingDescs) {
			if (desc.startsWith(prefix)) {
				String tail = desc.substring(prefix.length());
				try {
					int n = Integer.parseInt(tail);
					if (n > max)
						max = n;
				} catch (NumberFormatException ignored) {
				}
			}
		}

		logger.info("resolveNextCloneNumber originalTpName={} networkId={} existingMax={} nextNumber={}",
				originalTpName, networkId, max, max + 1);
		return max + 1;
	}

	// =====================================================
	// RESOLVE NEXT TP SUFFIX NUMBER (for service package / service plan)
	// Uses a single global interval across the CS_RAT_SERVICE_PACKAGE table.
	// Scans all SERVICE_PACKAGE_DESC ending with _TP<n> and returns max+1.
	// =====================================================
	private int resolveNextTpSuffixNumber() {
		List<String> existing = jdbcTemplate.queryForList("""
				select SERVICE_PACKAGE_DESC
				from CS_RAT_SERVICE_PACKAGE
				where REGEXP_LIKE(SERVICE_PACKAGE_DESC, '_TP[0-9]+$')
				""", String.class);

		int max = 0;
		for (String desc : existing) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("_TP(\\d+)$").matcher(desc);
			if (m.find()) {
				try {
					int n = Integer.parseInt(m.group(1));
					if (n > max)
						max = n;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		logger.info("resolveNextTpSuffixNumber existingMax={} nextNumber={}", max, max + 1);
		return max + 1;
	}

	// =====================================================
	// RESOLVE NEXT ATP SUFFIX NUMBER (for DATP / AATP packages)
	// Uses a single global interval across ATP records (add_pack_yn='Y').
	// Scans all SERVICE_PACKAGE_DESC ending with _ATP<n> and returns max+1.
	// This is called ONCE per executeTariffCreation call to get the starting
	// number; each individual ATP then increments the local counter so that
	// every ATP in the same TP gets its own unique suffix (ATP5, ATP6, ATP7 …).
	// =====================================================
	private int resolveNextAtpSuffixNumber() {
		List<String> existing = jdbcTemplate.queryForList("""
				select SERVICE_PACKAGE_DESC
				from CS_RAT_SERVICE_PACKAGE
				where ADD_PACK_YN = 'Y'
				  and REGEXP_LIKE(SERVICE_PACKAGE_DESC, '_ATP[0-9]+$')
				""", String.class);

		int max = 0;
		for (String desc : existing) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("_ATP(\\d+)$").matcher(desc);
			if (m.find()) {
				try {
					int n = Integer.parseInt(m.group(1));
					if (n > max)
						max = n;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		logger.info("resolveNextAtpSuffixNumber existingMax={} nextNumber={}", max, max + 1);
		return max + 1;
	}

	// =====================================================
	// REJECT TARIFF
	// =====================================================
	public Map<String, Object> reject(String tpName, String remarks) {
		Map<String, Object> json = (Map<String, Object>) jsonStorage.getTpData(tpName);
		if (json == null) {
			Map<String, Object> err = new HashMap<>();
			err.put("status", "error");
			err.put("message", "JSON NOT FOUND for tpName=" + tpName);
			return err;
		}

		// Save to rejected-tariffs.json before removing from pending
		Map<String, Object> rejectedEntry = new LinkedHashMap<>(json);
		rejectedEntry.put("rejectedOn", java.time.LocalDateTime.now().toString());
		rejectedEntry.put("remarks", remarks != null ? remarks : "");
		jsonStorage.storeRejected(tpName, rejectedEntry);

		removeFromJson(tpName);
		logger.info("Tariff rejected successfully tpName={} remarks={}", tpName, remarks);
		Map<String, Object> result = new HashMap<>();
		result.put("status", "success");
		result.put("message", "Tariff rejected successfully");
		return result;
	}

	// =====================================================
	// SHARED CORE: executeTariffCreation
	// =====================================================
	private Map<String, Object> executeTariffCreation(Map<String, Object> data, String tpName, Long networkId,
			String username) {

		// Shared counter for chargeId generation: tpName_PR1, tpName_PR2, ...
		// Resets per tariff creation call.
		AtomicInteger prCounter = new AtomicInteger(0);

		// tpSuffix: suffix appended to SERVICE_PACKAGE_DESC / SERVICE_PLAN_DESC for
		// base service packages. Uses a global incrementing interval -> TP1, TP2, TP3 ...
		int tpSuffixNumber = resolveNextTpSuffixNumber();
		String tpSuffix = "TP" + tpSuffixNumber;

		// atpSuffixCounter: starting number for per-ATP suffix generation.
		// Each ATP in this TP gets its own unique suffix -> ATP5, ATP6, ATP7 ...
		// The counter is incremented inside the loop (STEP 5) so all DATPs and AATPs
		// within a single tariff creation share one monotonically increasing sequence.
		int atpSuffixCounter = resolveNextAtpSuffixNumber();

		logger.info("executeTariffCreation tpName={} tpSuffix={} atpSuffixStartingAt=ATP{}",
				tpName, tpSuffix, atpSuffixCounter);

		try {
			// ── STEP 1 ──────────────────────────────────────────────────────
			Long oldServicePackageId = Long.valueOf(data.get("tariffPlanId").toString());
			Long oldPlanId = serviceCloneService.getOldPlanId(networkId, oldServicePackageId);
			if (oldPlanId == null) {
				throw new RuntimeException("Old plan not found for packageId=" + oldServicePackageId);
			}
			Long oldPlanZoneId = serviceCloneService.getPlanZoneId(oldPlanId);

			// ── STEP 2 ──────────────────────────────────────────────────────
			List<Map<String, Object>> defaultAtps = (List<Map<String, Object>>) data.get("defaultAtps");
			List<Map<String, Object>> addAtps = (List<Map<String, Object>>) data.get("allowedAtps");

			boolean hasDefaultAtps = defaultAtps != null && !defaultAtps.isEmpty();
			boolean hasAllowedAtps = addAtps != null && !addAtps.isEmpty();
			boolean hasAnyAtps = hasDefaultAtps || hasAllowedAtps;

			logger.info("ATP presence check hasDefaultAtps={} hasAllowedAtps={} hasAnyAtps={}", hasDefaultAtps,
					hasAllowedAtps, hasAnyAtps);

			Long oldBucketZoneId = null;

			if (hasAnyAtps) {
				List<Map<String, Object>> firstAtpList = hasDefaultAtps ? defaultAtps : addAtps;
				Long firstOldAtpId = Long.valueOf(firstAtpList.get(0).get("servicePackageId").toString());
				String oldBucketId = bundleService.getOldBucketId(firstOldAtpId, networkId);
				if (oldBucketId == null) {
					throw new RuntimeException("Old bucket not found for ATP=" + firstOldAtpId);
				}
				oldBucketZoneId = bundleService.getBucketZoneId(oldBucketId);
				logger.info("Old mapping oldPlanId={} oldPlanZoneId={} oldBucketId={} oldBucketZoneId={}", oldPlanId,
						oldPlanZoneId, oldBucketId, oldBucketZoneId);
			} else {
				logger.info("No ATPs present. Skipping bucket zone resolution.");
			}

			// ── STEP 3 ──────────────────────────────────────────────────────
			// Zone cloning uses tpSuffix for the plan zone.
			// For the bucket zone, we use the first ATP's suffix (atpSuffixCounter as-is),
			// since all ATPs share a single newBucketZoneId for this TP.
			Long newPlanZoneId;
			Long newBucketZoneId;

			if (hasAnyAtps) {
				String firstAtpSuffix = "ATP" + atpSuffixCounter;
				if (oldPlanZoneId != null && oldPlanZoneId.equals(oldBucketZoneId)) {
					Long newZoneId = servicePlanZone.generateNewZoneId();
					newPlanZoneId = newZoneId;
					newBucketZoneId = newZoneId;
					servicePlanZone.cloneZoneIfExists(oldPlanZoneId, newZoneId, networkId, tpSuffix);
				} else {
					newPlanZoneId = servicePlanZone.generateNewZoneId();
					servicePlanZone.cloneZoneIfExists(oldPlanZoneId, newPlanZoneId, networkId, tpSuffix);
					newBucketZoneId = servicePlanZone.generateNewZoneId();
					servicePlanZone.cloneZoneIfExists(oldBucketZoneId, newBucketZoneId, networkId, firstAtpSuffix);
				}
			} else {
				newPlanZoneId = servicePlanZone.generateNewZoneId();
				servicePlanZone.cloneZoneIfExists(oldPlanZoneId, newPlanZoneId, networkId, tpSuffix);
				newBucketZoneId = null;
				logger.info("No ATPs present. newPlanZoneId={} newBucketZoneId=null.", newPlanZoneId);
			}

			// ── STEP 4 ──────────────────────────────────────────────────────
			CloneServiceResult serviceResult = serviceCloneService.cloneService(networkId, oldServicePackageId,
					tpSuffix, newPlanZoneId);

			Long newServicePackageId = serviceResult.getNewPackageId();
			Long newServicePlanId = serviceResult.getNewPlanId();

			logger.info("Base service cloned newServicePackageId={} newServicePlanId={}", newServicePackageId,
					newServicePlanId);

			// ── STEP 5 ──────────────────────────────────────────────────────
			// Each ATP gets its own unique suffix (ATP5, ATP6, ATP7 ...) by incrementing
			// atpSuffixCounter inside the loop. DATPs are processed first, then AATPs,
			// so the sequence is continuous across both lists.
			List<Long> defaultAtpIds = new ArrayList<>();
			List<Long> allowedAtpIds = new ArrayList<>();
			List<Long> newAtpIds = new ArrayList<>();

			if (hasAnyAtps) {

				if (hasDefaultAtps) {
					for (Map<String, Object> atp : defaultAtps) {
						// ── Unique suffix for this specific ATP ──────────────
						String atpSuffix = "ATP" + atpSuffixCounter++;

						String chargeId = tpName + "_"+ atpSuffix;
						atp.put("chargeId", chargeId);
						Long oldAtpId = Long.valueOf(atp.get("servicePackageId").toString());

						CloneAtpResult atpResult = bundleService.cloneAtpData(
								oldAtpId, networkId, atpSuffix, newBucketZoneId);

						defaultAtpIds.add(atpResult.getNewAtpId());
						newAtpIds.add(atpResult.getNewAtpId());

						logger.info("Default ATP cloned oldAtpId={} newAtpId={} atpSuffix={} chargeId={}",
								oldAtpId, atpResult.getNewAtpId(), atpSuffix, chargeId);
					}
				}

				if (hasAllowedAtps) {
					for (Map<String, Object> atp : addAtps) {
						// ── Unique suffix for this specific ATP ──────────────
						String atpSuffix = "ATP" + atpSuffixCounter++;

						String chargeId = tpName + "_"+ atpSuffix;
						atp.put("chargeId", chargeId);
						Long oldAtpId = Long.valueOf(atp.get("servicePackageId").toString());

						CloneAtpResult atpResult = bundleService.cloneAtpData(
								oldAtpId, networkId, atpSuffix, newBucketZoneId);

						allowedAtpIds.add(atpResult.getNewAtpId());
						newAtpIds.add(atpResult.getNewAtpId());

						logger.info("Allowed ATP cloned oldAtpId={} newAtpId={} atpSuffix={} chargeId={}",
								oldAtpId, atpResult.getNewAtpId(), atpSuffix, chargeId);
					}
				}

			} else {
				logger.info("No ATPs present. Skipping bundle and bucket clone.");
			}

			logger.info("Final defaultAtpIds={} allowedAtpIds={}", defaultAtpIds, allowedAtpIds);

			// ── STEP 6: Periodic charge — one insert per ATP ─────────────────
			if (hasDefaultAtps) {
				for (Map<String, Object> atp : defaultAtps) {
					insertPeriodicChargeForAtp(atp, data, networkId, username);
				}
			}
			if (hasAllowedAtps) {
				for (Map<String, Object> atp : addAtps) {
					insertPeriodicChargeForAtp(atp, data, networkId, username);
				}
			}

			if (!hasAnyAtps) {
				logger.info("No ATPs. Skipping periodic charge insert.");
			}
            
			String oldChargeId = data.get("periodicChargeID") != null ? data.get("periodicChargeID").toString() : "";

		    String newChargeId = "";
			if (!oldChargeId.isBlank()) {
				newChargeId = clonePeriodicCharge(
						oldChargeId,
						networkId,
						tpSuffix,
						username);
				logger.info("TP Periodic Charge cloned oldChargeId={} newChargeId={}",
						oldChargeId, newChargeId);
			} else {
				logger.warn("periodicChargeID is empty — skipping charge clone for tpSuffix={}", tpSuffix);
			}
		
			// ── STEP 7: Create tariff package ─────────
			Long tariffId;
			try {
				tariffId = jdbcTemplate.queryForObject("select SEQ_TARIFF_PACK_ID.nextval from dual", Long.class);

				jdbcTemplate.update("""
						insert into CS_RAT_TARIFF_PACKAGE
						(
						    TARIFF_PACKAGE_ID,
						    TARIFF_PACKAGE_DESC,
						    NETWORK_ID,
						    END_DATE,
						    PUBLICITY_ID,
						    PACKAGE_TYPE,
						    IS_CORPORATE_YN,
						    TARIFF_PACK_CATEGORY,
							CHARGE_ID
						)
						values (?,?,?,?,?,?,?,?,?)
						""", tariffId, data.get("tariffPackageDesc"), networkId,
						Date.valueOf(LocalDate.parse(data.get("endDate").toString(), formatter)),
						data.get("publicityId"), data.get("packageType"), convertYN(data.get("isCorporateYn")),
						data.get("tariffPackCategory"), newChargeId);
			} catch (Exception ex) {
				throw new TariffInsertException("STEP 7", "CS_RAT_TARIFF_PACKAGE", ex);
			}

			// ── STEP 8: Publicity mapping ────────────────────────────────────
			try {
				jdbcTemplate.update("""
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
						""", networkId, tariffId, data.get("tariffPackageDesc"), data.get("publicityId"), username);
			} catch (Exception ex) {
				throw new TariffInsertException("STEP 8", "CS_RAT_TPID_VS_PUBLICITYID", ex);
			}

			// ── STEP 9: Map tariff to base service package ───────────────────
			try {
				jdbcTemplate.update("""
						insert into CS_RAT_TARIFF_SERVICE_PACK_MAP
						(
						    TARIFF_PACKAGE_ID,
						    SERVICE_PACKAGE_ID,
						    NETWORK_ID,
						    TARIFF_PLAN_TYPE,CHARGE_ID
						)
						values (?,?,?,?,?)
						""", tariffId, newServicePackageId, networkId, "TP", newChargeId);
			} catch (Exception ex) {
				throw new TariffInsertException("STEP 9", "CS_RAT_TARIFF_SERVICE_PACK_MAP", ex);
			}

			// ── STEP 10: Map tariff to ATPs (use per-ATP chargeId) ───────────
			int datpIdx = 0;
			for (Long atpId : defaultAtpIds) {
				Object priorityObj = defaultAtps.get(defaultAtpIds.indexOf(atpId)).get("priority");
				String priorityStr = priorityObj != null ? priorityObj.toString().trim() : "";
				Integer priorityValue = priorityStr.isEmpty() ? 0 : Integer.valueOf(priorityStr);
				String atpChargeId = defaultAtps.get(datpIdx).get("chargeId").toString();
				try {
					jdbcTemplate.update("""
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
							""", tariffId, atpId, networkId, "DATP", atpChargeId, priorityValue, 30);
				} catch (Exception ex) {
					throw new TariffInsertException("STEP 10", "CS_RAT_TARIFF_SERVICE_PACK_MAP(DATP)", ex);
				}
				datpIdx++;
			}

			int aatpIdx = 0;
			for (Long atpId : allowedAtpIds) {
				Object priorityObj = addAtps.get(allowedAtpIds.indexOf(atpId)).get("priority");
				String priorityStr = priorityObj != null ? priorityObj.toString().trim() : "";
				Integer priorityValue = priorityStr.isEmpty() ? 0 : Integer.valueOf(priorityStr);
				String atpChargeId = addAtps.get(aatpIdx).get("chargeId").toString();
				try {
					jdbcTemplate.update("""
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
							""", tariffId, atpId, networkId, "AATP", atpChargeId, priorityValue, 30);
				} catch (Exception ex) {
					throw new TariffInsertException("STEP 10", "CS_RAT_TARIFF_SERVICE_PACK_MAP(AATP)", ex);
				}
				aatpIdx++;
			}

			Map<String, Object> response = new HashMap<>();
			response.put("tariffPackageId", tariffId);
			response.put("newServicePackageId", newServicePackageId);
			response.put("newServicePlanId", newServicePlanId);
			response.put("newAtpIds", newAtpIds);
			response.put("newPlanZoneId", newPlanZoneId);
			response.put("newBucketZoneId", newBucketZoneId);
			return response;

		} catch (TariffInsertException tie) {
			throw tie;
		} catch (Exception ex) {
			logger.error("Tariff creation failed tpName={} error={}", tpName, ex.getMessage(), ex);
			throw ex;
		}
	}

	// ── Insert one periodic charge row per ATP ────────────────────────────────
	private void insertPeriodicChargeForAtp(Map<String, Object> atp, Map<String, Object> data, Long networkId,
			String username) {

		String chargeId = atp.get("chargeId").toString();

		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from CS_RAT_PERIODIC_CHARGE_INFO
				where CHARGE_ID = ?
				and NETWORK_ID = ?
				""", Integer.class, chargeId, networkId);

		if (count != null && count > 0) {
			logger.info("Periodic charge already exists chargeId={}", chargeId);
			return;
		}

		Object validityDaysRaw = atp.get("rentalPeriod");
		Object rentalPeriod;
		if ("O".equals(atp.get("validity")) && validityDaysRaw != null
				&& !validityDaysRaw.toString().trim().isEmpty()) {
			try {
				rentalPeriod = Integer.parseInt(validityDaysRaw.toString().trim());
			} catch (NumberFormatException e) {
				rentalPeriod = 1;
			}
		} else {
			rentalPeriod = 1;
		}

		try {
			jdbcTemplate.update("""
					insert into CS_RAT_PERIODIC_CHARGE_INFO
					(
					    CHARGE_ID,
					    CHARGE_DESC,
					    NETWORK_ID,
					    SERVICE_TYPE,
					    RENTAL_TYPE,
					    RENTAL_PERIOD,
					    ACTIVATION_FEE,
					    RENTAL_FEE,
					    RENTAL_FREE_CYCLES,
					    AUTO_RENEWAL,
					    PLAN_EXP_MIDNIGHT_YN,
					    MAX_RENEWAL_COUNT,
					    CREATED_BY
					)
					values (?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", chargeId, chargeId, networkId, data.get("tariffPlanId"), atp.get("validity"), rentalPeriod,
					data.get("charge"), atp.get("rental"), atp.get("freeCycles"), convertYN(atp.get("renewal")),
					convertYN(atp.get("midnightExpiry")), atp.get("maxCount"), username);
		} catch (Exception ex) {
			throw new TariffInsertException("STEP 6", "CS_RAT_PERIODIC_CHARGE_INFO", ex);
		}
	}

	private String convertYN(Object value) {
		if (value == null)
			return "N";
		String v = value.toString();
		if (v.equalsIgnoreCase("Y") || v.equalsIgnoreCase("YES") || v.equalsIgnoreCase("TRUE"))
			return "Y";
		return "N";
	}

	private void removeFromJson(String tpName) {
		Map<String, Object> json = (Map<String, Object>) jsonStorage.readAll();
		json.remove(tpName);
		jsonStorage.writeAll(json);
		logger.info("TP removed from json storage tpName={}", tpName);
	}


	private String clonePeriodicCharge(String oldChargeId,
                                   Long networkId,
                                   String tpSuffix,
                                   String username) {

    String newChargeId = oldChargeId + "_" + tpSuffix;

    Integer count = jdbcTemplate.queryForObject("""
            select count(*)
            from CS_RAT_PERIODIC_CHARGE_INFO
            where CHARGE_ID = ?
              and NETWORK_ID = ?
            """,
            Integer.class,
            newChargeId,
            networkId);

    if (count != null && count > 0) {
        return newChargeId;
    }

    jdbcTemplate.update("""
            insert into CS_RAT_PERIODIC_CHARGE_INFO
            (
                CHARGE_ID,
                CHARGE_DESC,
                NETWORK_ID,
                SERVICE_TYPE,
                RENTAL_TYPE,
                RENTAL_PERIOD,
                ALCS_ID,
                ACTIVATION_FEE,
                RENTAL_FEE,
                RENTAL_FREE_CYCLES,
                PRO_RAT_YN,
                ADV_YN,
                TAX1,
                TAX2,
                TAX3,
                ADD_PACK_CHARGE_YN,
                RENTAL_DEDUCTION_IN_GRACE,
                DEACTIVATION_FEE,
                AUTO_RENEWAL,
                CREATED_BY,
                CREATED_DATE,
                PLAN_EXP_MIDNIGHT_YN,
                RESERVED_AMOUNT,
                MAX_RENEWAL_COUNT,
                SALES_FEE
            )
            select
                ?,                                
                CHARGE_DESC || '_' || ?,        
                NETWORK_ID,
                SERVICE_TYPE,
                RENTAL_TYPE,
                RENTAL_PERIOD,
                ALCS_ID,
                ACTIVATION_FEE,
                RENTAL_FEE,
                RENTAL_FREE_CYCLES,
                PRO_RAT_YN,
                ADV_YN,
                TAX1,
                TAX2,
                TAX3,
                ADD_PACK_CHARGE_YN,
                RENTAL_DEDUCTION_IN_GRACE,
                DEACTIVATION_FEE,
                AUTO_RENEWAL,
                ?,
                SYSDATE,
                PLAN_EXP_MIDNIGHT_YN,
                RESERVED_AMOUNT,
                MAX_RENEWAL_COUNT,
                SALES_FEE
            from CS_RAT_PERIODIC_CHARGE_INFO
            where CHARGE_ID = ?
              and NETWORK_ID = ?
            """,
            newChargeId,
            tpSuffix,
            username,
            oldChargeId,
            networkId);

    return newChargeId;
}
}