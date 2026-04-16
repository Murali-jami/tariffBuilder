package com.xius.TariffBuilder.UserService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xius.TariffBuilder.Entity.SaveConfigDao;
import com.xius.TariffBuilder.util.JsonStorage;

@Service
public class SaveConfigService {

	private static final Logger logger = LoggerFactory.getLogger(SaveConfigService.class);
	@Autowired
	private SaveConfigDao dao;

	@Autowired
	private JsonStorage jsonStorage;

	public Map<String, Object> prepareConfig(

			Map<String, Object> request,

			String username,

			Long networkId) {

		logger.info("Preparing config for user={} networkId={}", username, networkId);

		Map<String, Object> response = new HashMap<>();

		// Read required fields
		String tpName = (String) request.get("tariffPackageDesc");
		String publicityId = (String) request.get("publicityId");
		Number tariffPlanId = (Number) request.get("tariffPlanId");

		logger.debug("Extracted fields tpName={} publicityId={} tariffPlanId={}",
				tpName, publicityId, tariffPlanId);

		// BASIC VALIDATION
		if (tpName == null || tpName.isBlank()) {

			logger.warn("Validation failed: tpName missing username={}", username);
			response.put("error", "tariffPackageDesc required");
			return response;
		}

		if (publicityId == null) {

			logger.warn("Validation failed: publicityId missing tpName={}", tpName);
			response.put("error", "publicityId required");
			return response;
		}

		if (tariffPlanId == null) {

			logger.warn("Validation failed: tariffPlanId missing tpName={}", tpName);
			response.put("error", "tariffPlanId required");
			return response;
		}

		// DB VALIDATION
		if (dao.checkTariffExists(networkId, tpName)) {

			logger.warn("DB validation failed: tariff exists tpName={} networkId={}", tpName, networkId);
			response.put("error", "Tariff already exists in DB");
			return response;
		}

		if (dao.checkPublicityExists(networkId, publicityId)) {

			logger.warn("DB validation failed: publicity exists publicityId={} networkId={}", publicityId, networkId);
			response.put("error", "Publicity already mapped in DB");
			return response;
		}

		// DATP VALIDATION
		List<Map<String, Object>> datp = (List<Map<String, Object>>) request.get("defaultAtps");

		if (datp != null) {

			for (Map<String, Object> atp : datp) {

				logger.debug("Validating DATP entry={}", atp);

				if (atp.get("servicePackageId") == null || atp.get("chargeId") == null) {

					logger.warn("DATP validation failed invalid entry tpName={} entry={}", tpName, atp);

					response.put("error", "DATP requires servicePackageId and chargeId");

					return response;
				}
			}
		}

		// AATP VALIDATION
		List<Map<String, Object>> aatp = (List<Map<String, Object>>) request.get("allowedAtps");

		if (aatp != null) {

			for (Map<String, Object> atp : aatp) {

				logger.debug("Validating AATP entry={}", atp);

				if (atp.get("servicePackageId") == null || atp.get("chargeId") == null) {

					logger.warn("AATP validation failed invalid entry tpName={} entry={}", tpName, atp);

					response.put("error", "AATP requires servicePackageId and chargeId");

					return response;
				}
			}
		}

		// JSON DUPLICATE CHECK
		if (jsonStorage.exists(tpName)) {

			logger.warn("JSON validation failed: config already exists tpName={}", tpName);
			response.put("error", "Tariff already prepared in JSON");

			return response;
		}

		// STORE JSON
		logger.info("Storing config tpName={} username={} networkId={}",
				tpName, username, networkId);
		jsonStorage.store(tpName, username, networkId, request);

		// SUCCESS RESPONSE
		logger.info("Config prepared successfully tpName={} username={} networkId={}", tpName, username, networkId);

		response.put("message", "Configuration prepared successfully");

		response.put("tpName", tpName);

		return response;
	}

	public void saveDraft(Map<String, Object> draft, String username) {

		logger.info("Saving draft for user={} draftName={}", username, draft.get("name"));

		try {
			ObjectMapper mapper = new ObjectMapper();

			Path path = Paths.get("drafts", username + ".json");

			logger.debug("Draft file path={}", path);

			Files.createDirectories(path.getParent());

			List<Map<String, Object>> drafts = new ArrayList<>();

			if (Files.exists(path) && Files.size(path) > 0) {
				drafts = mapper.readValue(
						path.toFile(),
						new TypeReference<List<Map<String, Object>>>() {
						});
			}

			logger.debug("Removing existing draft with same name={}", draft.get("name"));

			boolean shouldDelete = Boolean.TRUE.equals(draft.get("_delete"));
			drafts.removeIf(d -> d.get("name").equals(draft.get("name")));
			if (shouldDelete) {
				logger.info("Deleting draft username={} draftName={}", username, draft.get("name"));
				mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), drafts);
				return;
			}
			drafts.add(0, draft);

			mapper.writerWithDefaultPrettyPrinter()
					.writeValue(path.toFile(), drafts);

			logger.info("Draft saved successfully username={} draftName={}", username, draft.get("name"));

		} catch (Exception e) {
			logger.error("Error saving draft username={} error={}", username, e.getMessage(), e);
		}
	}
}