package com.xius.TariffBuilder.UserService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.Entity.ServicePlanPackMap;

@Service
public class ServicePlanService {

	private static final Logger logger = LoggerFactory.getLogger(ServicePlanService.class);

	@Autowired
	@Qualifier("oracleJdbcTemplate")
	private JdbcTemplate jdbcTemplate;

	// Exclude service packages whose name ends with a cloned suffix:
	// _TP<n> – step-2 service package clone
	// _CL<n> – legacy clone suffix
	// _ATP<n> – DATP / AATP clone
	private static final String EXCLUDE_CLONED_CONDITION = "NOT REGEXP_LIKE(a.service_package_desc, '_(TP|CL|ATP)[0-9]+$')";

	public List<ServicePlanPackMap> getPlans(Long networkId, String types) {

		logger.info("Fetching TP plans networkId={} types={}", networkId, types);

		String sql = """
			SELECT *
				FROM (
				    SELECT
				        x.service_package_id AS SERVICE_PACKAGE_ID,
				        x.service_package_desc AS SERVICE_PACKAGE_NAME,
				        x.network_id AS NETWORK_ID,
				        'TP' AS TARIFF_PLAN_TYPE,
				        LISTAGG(x.type_of_service, ',')
				            WITHIN GROUP (ORDER BY x.type_of_service) AS SERVICE_TYPES
				    FROM (
				        SELECT DISTINCT
				            spkg.service_package_id,
				            spkg.service_package_desc,
				            spkg.network_id,
				            spl.type_of_service
				        FROM cs_rat_service_package spkg
				        JOIN cs_rat_service_plan_package spp
				            ON spkg.service_package_id = spp.service_package_id
				           AND spkg.network_id = spp.network_id
				        JOIN cs_rat_service_plans spl
				            ON spp.service_plan_id = spl.service_plan_id
				           AND spp.network_id = spl.network_id
				        WHERE spkg.network_id = ?
				          AND spkg.add_pack_yn = 'N'
				          AND spl.type_of_service IN (1,2,3)
				          AND NOT REGEXP_LIKE(
				                spkg.service_package_desc,
				                '_(TP|CL|ATP)[0-9]+$'
				          )
				    ) x
				    GROUP BY
				        x.service_package_id,
				        x.service_package_desc,
				        x.network_id
				)
				WHERE SERVICE_TYPES = ?
								            """;

		return map(sql, networkId, types);
	}

	public List<ServicePlanPackMap> getDAtpPlans(Long networkId, String types) {

		logger.info("Fetching DATP plans networkId={} types={}", networkId, types);

		String sql = """
				SELECT *
				FROM (
				    SELECT
				        x.service_package_id AS SERVICE_PACKAGE_ID,
				        x.service_package_desc AS SERVICE_PACKAGE_NAME,
				        x.network_id AS NETWORK_ID,
				        'ATP' AS TARIFF_PLAN_TYPE,
				        LISTAGG(x.service_type, ',')
				            WITHIN GROUP (ORDER BY x.sort_order) AS SERVICE_TYPES
				    FROM (
				        SELECT DISTINCT
				            a.service_package_id,
				            a.service_package_desc,
				            a.network_id,

				            CASE f.balance_category
				                WHEN 'VOICE' THEN '1'
				                WHEN 'SMS' THEN '2'
				                WHEN 'DATA' THEN '3'
				            END AS service_type,

				            CASE f.balance_category
				                WHEN 'VOICE' THEN 1
				                WHEN 'SMS' THEN 2
				                WHEN 'DATA' THEN 3
				            END AS sort_order

				        FROM cs_rat_service_package a
				        JOIN cs_atp_accumu_bon_disc_map d
				            ON a.service_package_id = d.atp_id
				        JOIN cs_bndl_mt_bndl_bucket_map e
				            ON d.bundle_or_discount_id = e.bundle_id
				        JOIN bndl_mt_buckets f
				            ON e.bucket_id = f.bucket_id
				        WHERE a.network_id = ?
				          AND a.add_pack_yn = 'Y'
				          AND NOT REGEXP_LIKE(a.service_package_desc, '_(TP|CL|ATP)[0-9]+$')
				    ) x
				    GROUP BY
				        x.service_package_id,
				        x.service_package_desc,
				        x.network_id
				)
				WHERE SERVICE_TYPES = ?
				""";

		return map(sql, networkId, types);
	}

	public List<ServicePlanPackMap> getAAtpPlans(Long networkId, String types) {

		logger.info("Fetching AATP plans networkId={} types={}", networkId, types);

		String sql = """
				SELECT *
				FROM (
				    SELECT
				        x.service_package_id AS SERVICE_PACKAGE_ID,
				        x.service_package_desc AS SERVICE_PACKAGE_NAME,
				        x.network_id AS NETWORK_ID,
				        'ATP' AS TARIFF_PLAN_TYPE,
				        LISTAGG(x.service_type, ',')
				            WITHIN GROUP (ORDER BY x.sort_order) AS SERVICE_TYPES
				    FROM (
				        SELECT DISTINCT
				            a.service_package_id,
				            a.service_package_desc,
				            a.network_id,

				            CASE f.balance_category
				                WHEN 'VOICE' THEN '1'
				                WHEN 'SMS' THEN '2'
				                WHEN 'DATA' THEN '3'
				            END AS service_type,

				            CASE f.balance_category
				                WHEN 'VOICE' THEN 1
				                WHEN 'SMS' THEN 2
				                WHEN 'DATA' THEN 3
				            END AS sort_order

				        FROM cs_rat_service_package a
				        JOIN cs_atp_accumu_bon_disc_map d
				            ON a.service_package_id = d.atp_id
				        JOIN cs_bndl_mt_bndl_bucket_map e
				            ON d.bundle_or_discount_id = e.bundle_id
				        JOIN bndl_mt_buckets f
				            ON e.bucket_id = f.bucket_id
				        WHERE a.network_id = ?
				          AND a.add_pack_yn = 'Y'
				          AND NOT REGEXP_LIKE(a.service_package_desc, '_(TP|CL|ATP)[0-9]+$')
				    ) x
				    GROUP BY
				        x.service_package_id,
				        x.service_package_desc,
				        x.network_id
				)
				WHERE SERVICE_TYPES = ?
				""";

		return map(sql, networkId, types);
	}

	private List<ServicePlanPackMap> map(String sql, Long networkId, String types) {

		logger.info("Executing query networkId={} types=[{}]", networkId, types);

		List<ServicePlanPackMap> result = jdbcTemplate.query(sql, (rs, rowNum) -> {

			ServicePlanPackMap s = new ServicePlanPackMap();

			s.setServicePackageId(rs.getString("SERVICE_PACKAGE_ID"));
			s.setServicePackageName(rs.getString("SERVICE_PACKAGE_NAME"));
			s.setNetworkId(rs.getInt("NETWORK_ID"));
			s.setTariffPlanType(rs.getString("TARIFF_PLAN_TYPE"));
			s.setServiceTypes(rs.getString("SERVICE_TYPES"));

			return s;

		}, networkId, types);

		logger.info("Rows returned={}", result.size());

		return result;
	}
}