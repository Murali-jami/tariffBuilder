package com.xius.TariffBuilder.Entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SaveConfigDao {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public boolean checkTariffExists(

			Long networkId,

			String tpName) {

		String sql = """
				SELECT COUNT(1)
				FROM cs_rat_tariff_package
				WHERE network_id = ?
				AND UPPER(tariff_package_desc)=UPPER(?)
				""";

		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, networkId, tpName);
		return count > 0;
	}

	public boolean checkPublicityExists(Long networkId, String publicityId) {

		String sql = """
				SELECT COUNT(1)
				FROM cs_rat_tariff_package
				WHERE network_id = ?
				AND UPPER(publicity_id)=UPPER(?)
				""";

		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, networkId, publicityId);
		return count > 0;
	}
}
