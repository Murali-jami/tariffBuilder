package com.xius.TariffBuilder.UserService;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.util.JsonStorage;

@Service
public class TariffService {

	private static final Logger logger = LoggerFactory.getLogger(TariffService.class);

	@Autowired
	private JsonStorage jsonStorage;

	public List<String> getTariffPackages() {

		logger.info("Fetching Tariff Package names from JSON");

		return new ArrayList<>(jsonStorage.getAllTpNames());
	}

	public Object getHierarchy(String tpName) {

		logger.info("Fetching hierarchy for tpName={}", tpName);

		return jsonStorage.getTpData(tpName);
	}
}