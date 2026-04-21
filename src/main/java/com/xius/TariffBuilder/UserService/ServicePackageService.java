package com.xius.TariffBuilder.UserService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ServicePackageService {

	private static final Logger logger = LoggerFactory.getLogger(ServicePackageService.class);

	@Autowired
	@Qualifier("oracleJdbcTemplate")
	private JdbcTemplate jdbcTemplate;

	public String getDescription(Long servicePackageId, Long networkId) {

		logger.info("getDescription called servicePackageId={} networkId={}", servicePackageId, networkId);

		String sql = """
				SELECT DESCRIPTION
				FROM CS_RAT_SERVICE_PACKAGE
				WHERE SERVICE_PACKAGE_ID = ?
				AND NETWORK_ID = ?
				""";

		logger.debug("Executing SQL to fetch DESCRIPTION");

		List<String> list = jdbcTemplate.query(sql, new Object[] { servicePackageId, networkId },
				(rs, i) -> rs.getString("DESCRIPTION"));

		logger.debug("Query result size={}", list.size());

		if (list.isEmpty()) {

			logger.info("Description not found for servicePackageId={} networkId={}", servicePackageId, networkId);

			return "Description not found";
		}

		String description = list.get(0);

		logger.info("Description fetched successfully");

		return description;
	}
}