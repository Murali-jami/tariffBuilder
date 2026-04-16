package com.xius.TariffBuilder.UserService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
public class ServiceCloneService {

	private static final Logger logger = LoggerFactory.getLogger(ServiceCloneService.class);

//	@Autowired
//	private JdbcTemplate jdbcTemplate;
	
	    @Autowired
		@Qualifier("oracleJdbcTemplate")
		private JdbcTemplate jdbcTemplate;

	/*
	 * ===================================================== VALIDATION
	 * =====================================================
	 */

	public boolean isTpNameExists(Long networkId, String tpName) {

		logger.info("Checking TP name already exists networkId={} tpName={}", networkId, tpName);

		String pkgSql = """
				select count(*)
				from CS_RAT_SERVICE_PACKAGE
				where NETWORK_ID=?
				and SERVICE_PACKAGE_DESC like '%'||?
				""";

		Integer pkgCount = jdbcTemplate.queryForObject(pkgSql, Integer.class, networkId, "_" + tpName);

		String planSql = """
				select count(*)
				from CS_RAT_SERVICE_PLANS
				where NETWORK_ID=?
				and SERVICE_PLAN_DESC like '%'||?
				""";

		Integer planCount = jdbcTemplate.queryForObject(planSql, Integer.class, networkId, "_" + tpName);

		logger.debug("TP validation result pkgCount={} planCount={}", pkgCount, planCount);

		return (pkgCount > 0 || planCount > 0);
	}

	/*
	 * ===================================================== CLONE SERVICE
	 * =====================================================
	 */

	@Transactional
	public Long cloneService(Long networkId, Long servicePackageId, String tpName) {

		logger.info("Clone Service started networkId={} servicePackageId={} tpName={}", networkId, servicePackageId,
				tpName);

		/*
		 * ===================================================== STEP 1 clone
		 * CS_RAT_SERVICE_PACKAGE =====================================================
		 */

		Long newPackageId = jdbcTemplate.queryForObject(

				"""
						select nvl(max(SERVICE_PACKAGE_ID),0)+1
						from CS_RAT_SERVICE_PACKAGE
						""",

				Long.class);

		logger.debug("Generated new SERVICE_PACKAGE_ID={}", newPackageId);

		jdbcTemplate.update(

				"""
						insert into CS_RAT_SERVICE_PACKAGE

						select ?,

						SERVICE_PACKAGE_DESC||'_'||?,

						RENTAL_AMOUNT,
						ACTIVATION_CHARGE,

						NETWORK_ID,

						TAX1,
						TAX2,
						TAX3,

						CHARGE_ID,

						ADD_PACK_YN,

						RENTAL_TYPE,
						RENTAL_PERIOD,

						ASP_TYPE,

						END_DATE,

						SERVICE_DURATION,

						ATP_CATEGORY,

						TRANSFEROR_CHARGE,
						TRANSFEREE_CHARGE,

						CHANGE_MSISDN_CHARGE,

						MAX_AMT_PER_TRANS,

						PUBLICITY_ID||'_'||?,

						MAX_FNFSERVICE_NUMBERS,
						MAX_SMSSERVICE_NUMBERS,

						CA_SERVICE_PACKAGE_YN,

						ATP_CATEGORY_BY_OFFER,

						DESCRIPTION,

						CHARGE_ON_FIRST_USAGE_YN,

						ALLOW_MULTIPLE_ATP_YN,

						USER_DEFINED_1,
						USER_DEFINED_2,
						USER_DEFINED_3

						from CS_RAT_SERVICE_PACKAGE

						where SERVICE_PACKAGE_ID=?
						""",

				newPackageId, tpName, tpName, servicePackageId);

		logger.info("SERVICE_PACKAGE cloned oldId={} newId={}", servicePackageId, newPackageId);

		/*
		 * ===================================================== STEP 2 get plan ids
		 * =====================================================
		 */

		List<Long> oldPlanIds = jdbcTemplate.queryForList(

				"""
						select SERVICE_PLAN_ID
						from CS_RAT_SERVICE_PLAN_PACKAGE
						where NETWORK_ID=?
						and SERVICE_PACKAGE_ID=?
						""",

				Long.class, networkId, servicePackageId);

		logger.debug("Found {} SERVICE_PLAN_IDs", oldPlanIds.size());

		/*
		 * ===================================================== STEP 3 clone plans
		 * =====================================================
		 */

		for (Long oldPlanId : oldPlanIds) {

			logger.debug("Cloning SERVICE_PLAN_ID={}", oldPlanId);

			Long newPlanId = jdbcTemplate.queryForObject(

					"""
							select nvl(max(SERVICE_PLAN_ID),0)+1
							from CS_RAT_SERVICE_PLANS
							""",

					Long.class);

			jdbcTemplate.update(

					"""
							insert into CS_RAT_SERVICE_PLANS

							select

							NETWORK_ID,

							?,

							SERVICE_PLAN_DESC||'_'||?,

							SERVICE_PLAN_TYPE,

							TYPE_OF_SERVICE,

							PRIORITY,

							LIMITED_HOURS_YN,

							SERVICE_PLAN_FREQ_FROM_HRS,
							SERVICE_PLAN_FREQ_TO_HRS,

							ALLOW_MTC,
							ALLOW_MOC,

							ALLOW_NLD_MO,
							ALLOW_ILD_MO,

							ALLOW_DATA,

							RATING_TYPE,

							ZONE_GROUP_ID,
							SMS_ZONE_GROUP_ID,

							NS_LOCAL_ONNET_CALENDAR_ID,
							NS_LOCAL_OFFNET_CALENDAR_ID,

							NS_NLD_CALENDAR_ID,
							NS_ILD_CALENDAR_ID,

							LIMITED_NETWORKS_YN,

							BNDL_WITH_SP_YN,

							BUNDLE_ID,

							SYSDATE,

							CREATED_BY,

							SMS_CALENDAR_ID,

							FNF_MAX_LINES,

							STATUS,

							FNF_MAX_GROUPS,

							GROUPS_ALLOWED,

							DATA_ZONE_GROUP_ID,

							ALLOW_NTNL_RM_DATA,
							ALLOW_INT_RM_DATA,

							RENTAL_DEDUCTION_IN_GRACE,

							MT_CALENDER_ID,

							DEVICE_GROUP_ID,

							MMS_CALENDAR_ID,

							PLAN_CONFIRM_NOTIFICATION,
							PLAN_EXP_NOTIFICATION,

							PLAN_EXP_NOTIF_THRESHOLD_DAYS,
							PLAN_EXP_NOTIF_THRESHOLD_HRS,

							ZONE_BASED_VIP_PLAN_FLAG_YN

							from CS_RAT_SERVICE_PLANS

							where SERVICE_PLAN_ID=?
							""",

					newPlanId, tpName, oldPlanId);

			/*
			 * ===================================================== STEP 4 map plan to
			 * package =====================================================
			 */

			jdbcTemplate.update(

					"""
							insert into CS_RAT_SERVICE_PLAN_PACKAGE

							(
							SERVICE_PACKAGE_ID,
							SERVICE_PLAN_ID,
							NETWORK_ID
							)

							values (?,?,?)
							""",

					newPackageId, newPlanId, networkId);

			logger.debug("Mapped newPlanId={} to newPackageId={}", newPlanId, newPackageId);

		}

		logger.info("Clone Service completed new SERVICE_PACKAGE_ID={}", newPackageId);

		return newPackageId;

	}

}