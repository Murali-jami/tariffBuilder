package com.xius.TariffBuilder.Dto;

import lombok.Data;

@Data
public class TariffPackageDetailsDto {
    
	private long tariff_package_id;
    private String tariffPackageDesc;
    private Double activationFee;
    private String rentalType;
    private String balanceCategory;
    private String bucketUnitValue;
    private String rateGroupName;
//    private Long rental_fee;
}