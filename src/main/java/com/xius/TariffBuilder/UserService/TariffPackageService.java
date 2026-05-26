package com.xius.TariffBuilder.UserService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.Dto.TariffPackageDetailsDto;

@Service
public class TariffPackageService {
 
	@Autowired
	@Qualifier("oracleJdbcTemplate")
	private JdbcTemplate jdbcTemplate;
 
	public List<TariffPackageDetailsDto> getTariffPackageDetails(Integer networkId) {
 
	    String sql = """
	        SELECT
	            a.tariff_package_id,
	            a.tariff_package_desc,
	            b.activation_fee,
 
	            DECODE(
	                rental_type,
	                'M', 'Monthly',
	                'O', 'Others',
	                'D', 'Daily',
	                'W', 'Weekly',
	                'F', 'Fixed',
	                'U', 'Unlimited',
	                'Y', 'Yearly'
	            ) rental_type,
 
	            f.balance_category,
 
	            CASE
	                WHEN f.balance_category = 'DATA'
	                THEN CASE
	                        WHEN SUM(f.bucket_unit_value) > 99999
	                        THEN 'UNLIMITED'
	                        ELSE fn_data_unit_converter(SUM(f.bucket_unit_value))
	                     END
 
	                WHEN f.balance_category = 'SMS'
	                THEN SUM(f.bucket_unit_value) || ' SMS'
 
	                WHEN f.balance_category = 'VOICE'
	                THEN SUM(f.bucket_unit_value) || ' Sec'
 
	                WHEN f.balance_category = 'GLOBAL'
	                THEN SUM(f.bucket_unit_value) || ' Amt'
	            END bucket_unit_value
 
	        FROM cs_rat_tariff_package a,
	             cs_rat_periodic_charge_info b,
	             cs_rat_tariff_service_pack_map c,
	             cs_atp_accumu_bon_disc_map d,
	             cs_bndl_mt_bndl_bucket_map e,
	             bndl_mt_buckets f
 
	        WHERE a.charge_id = b.charge_id
	        AND a.tariff_package_id = c.tariff_package_id
	        AND c.service_package_id = d.atp_id
	        AND d.bundle_or_discount_id = e.bundle_id
	        AND e.bucket_id = f.bucket_id
	        AND a.network_id = ?
	        AND tariff_plan_type = 'DATP'
 
	        GROUP BY
	            a.tariff_package_id,
	            a.tariff_package_desc,
	            b.activation_fee,
	            rental_type,
	            f.balance_category
 
	        ORDER BY b.activation_fee DESC
	        """;
 
	    return jdbcTemplate.query(sql, (rs, rowNum) -> {
 
	        TariffPackageDetailsDto dto = new TariffPackageDetailsDto();
 
	        dto.setTariff_package_id(rs.getLong("tariff_package_id"));
	        dto.setTariffPackageDesc(rs.getString("tariff_package_desc"));
	        dto.setActivationFee(rs.getDouble("activation_fee"));
	        dto.setRentalType(rs.getString("rental_type"));
	        dto.setBalanceCategory(rs.getString("balance_category"));
	        dto.setBucketUnitValue(rs.getString("bucket_unit_value"));
 
	        return dto;
 
	    }, networkId);
	}
	
}
 