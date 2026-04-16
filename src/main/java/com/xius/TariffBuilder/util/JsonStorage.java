package com.xius.TariffBuilder.util;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class JsonStorage {

	private static final Logger logger = LoggerFactory.getLogger(JsonStorage.class);

	private final ObjectMapper mapper = new ObjectMapper();

	private final String FILE_PATH = "json-storage/tariff-config.json";

	/*
	 * check if tp exists
	 */
	public boolean exists(String tpName) {

		logger.debug(
				"Checking JSON existence for tpName={}",
				tpName);

		try {

			File file = new File(FILE_PATH);

			if (!file.exists() || file.length() == 0) {

				return false;
			}

			Map<String, Object> json = mapper.readValue(

					file,

					new TypeReference<Map<String, Object>>() {
					});

			return json.containsKey(tpName);
		}

		catch (Exception e) {

			logger.error(
					"exists error",
					e);

			return false;
		}
	}

	/*
	 * store request json
	 */
	public void store(

			String tpName,

			String username,

			Long networkId,

			Map<String, Object> request) {

		logger.info(
				"Storing JSON tpName={}",
				tpName);

		try {

			File file = new File(FILE_PATH);

			file.getParentFile().mkdirs();

			Map<String, Object> json = new LinkedHashMap<>();

			if (file.exists() && file.length() > 0) {

				json = mapper.readValue(

						file,

						new TypeReference<Map<String, Object>>() {
						});
			}

			Map<String, Object> tpData = new LinkedHashMap<>();

			tpData.put(
					"tpName",
					tpName);

			tpData.put(
					"username",
					username);

			tpData.put(
					"networkId",
					networkId);

			tpData.put(
					"data",
					request);

			json.put(
					tpName,
					tpData);

			mapper
					.writerWithDefaultPrettyPrinter()
					.writeValue(
							file,
							json);

			logger.info(
					"JSON stored {}",
					tpName);

		}

		catch (Exception e) {

			logger.error(
					"store error",
					e);
		}
	}

	/*
	 * get all tp names
	 */
	public Set<String> getAllTpNames() {

		try {

			File file = new File(FILE_PATH);

			if (!file.exists() || file.length() == 0) {

				return Set.of();
			}

			Map<String, Object> json = mapper.readValue(

					file,

					new TypeReference<Map<String, Object>>() {
					});

			return json.keySet();
		}

		catch (Exception e) {

			logger.error(
					"getAllTpNames error",
					e);

			return Set.of();
		}
	}

	/*
	 * get single tp json
	 */
	public Object getTpData(String tpName) {

		logger.info(
				"Fetching JSON for {}",
				tpName);

		try {

			File file = new File(FILE_PATH);

			if (!file.exists() || file.length() == 0) {

				return null;
			}

			Map<String, Object> json = mapper.readValue(

					file,

					new TypeReference<Map<String, Object>>() {
					});

			return json.get(tpName);
		}

		catch (Exception e) {

			logger.error(
					"getTpData error",
					e);

			return null;
		}
	}

	/*
	 * read full json file
	 */
	public Map<String, Object> readAll() {

		try {

			File file = new File(FILE_PATH);

			if (!file.exists() || file.length() == 0) {

				return new LinkedHashMap<>();
			}

			return mapper.readValue(

					file,

					new TypeReference<Map<String, Object>>() {
					});
		}

		catch (Exception e) {

			logger.error(
					"readAll error",
					e);

			return new LinkedHashMap<>();
		}
	}

	/*
	 * write full json file
	 */
	public void writeAll(

			Map<String, Object> json) {

		try {

			File file = new File(FILE_PATH);

			mapper
					.writerWithDefaultPrettyPrinter()
					.writeValue(
							file,
							json);

			logger.info(
					"json updated");
		}

		catch (Exception e) {

			logger.error(
					"writeAll error",
					e);
		}
	}

	/*
	 * remove tp after approve/reject
	 */
	public void remove(

			String tpName) {

		try {

			Map<String, Object> json = readAll();

			json.remove(tpName);

			writeAll(json);

			logger.info(
					"json removed {}",
					tpName);

		}

		catch (Exception e) {

			logger.error(
					"remove error",
					e);
		}
	}

	/*
	 * get full json map
	 */
	public Map<String, Object> getAll() {

		return readAll();
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getByUser(String username) {

		Map<String, Object> all = readAll();

		Map<String, Object> result = new LinkedHashMap<>();

		for (Map.Entry<String, Object> entry : all.entrySet()) {

			Object value = entry.getValue();

			if (value instanceof Map) {

				Map<String, Object> tp = (Map<String, Object>) value;

				Object storedUser = tp.get("username");

				if (storedUser != null
						&& storedUser.toString().equals(username)) {

					result.put(
							entry.getKey(),
							tp);
				}
			}
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	public void removeByUser(String tpName, String username) {
		try {
			Map<String, Object> json = readAll();

			Object value = json.get(tpName);

			if (value instanceof Map) {
				Map<String, Object> tp = (Map<String, Object>) value;

				if (username.equals(tp.get("username"))) {
					json.remove(tpName);
					writeAll(json);
				}
			}

		} catch (Exception e) {
			logger.error("removeByUser error", e);
		}
	}

}