package com.xius.TariffBuilder.Dto;

import lombok.Data;

@Data
public class TariffPackageDetailsDto {

    public Long getTariff_package_id() {
		return tariff_package_id;
	}
	public void setTariff_package_id(Long tariff_package_id) {
		this.tariff_package_id = tariff_package_id;
	}
	public String getTariffPackageDesc() {
		return tariffPackageDesc;
	}
	public void setTariffPackageDesc(String tariffPackageDesc) {
		this.tariffPackageDesc = tariffPackageDesc;
	}
	public Double getActivationFee() {
		return activationFee;
	}
	public void setActivationFee(Double activationFee) {
		this.activationFee = activationFee;
	}
	public String getRentalType() {
		return rentalType;
	}
	public void setRentalType(String rentalType) {
		this.rentalType = rentalType;
	}
	
	public String getRental_Fee() {
		return rentalType;
	}
	public void setRental_Fee(String rentalType) {
		this.rentalType = rentalType;
	}
	
	public String getBalanceCategory() {
		return balanceCategory;
	}
	public void setBalanceCategory(String balanceCategory) {
		this.balanceCategory = balanceCategory;
	}
	public String getBucketUnitValue() {
		return bucketUnitValue;
	}
	public void setBucketUnitValue(String bucketUnitValue) {
		this.bucketUnitValue = bucketUnitValue;
	}
	private Long tariff_package_id;
    private String tariffPackageDesc;
    private Double activationFee;
    private String rentalType;
    private String balanceCategory;
    private String bucketUnitValue;
    private Long rental_fee;
}