package com.xius.TariffBuilder.Dto;

import java.util.Date;

public class TariffPackageDetails {

    private Long tariffPackageId;
    private String tariffPackageDesc;
    private Long networkId;
    private String packageType;
    private String isCorporateYn;
    private String tariffPackCategory;

    private Long servicePackageId;
    private String tariffPlanType;
    private String chargeId;
    private Long effectiveStartOffset;
    private Long serviceDuration;
    private Long priority;
    private Date createdOrModifiedDate;

    private Long servicePlanId;

    private String servicePackageDesc;
    private Long serviceType;
    private String rentalType;
    private Long rentalPeriod;
    private Long alcsId;

    private Double activationFee;
    private Double rentalFee;
    private Long rentalFreeCycles;

    private String proRatYn;
    private String advYn;

    private Double tax1;
    private Double tax2;
    private Double tax3;

    private String addPackChargeYn;
    private String rentalDeductionInGrace;
    private Double deactivationFee;
    private String autoRenewal;

    private String createdBy;
    private Date createdDate;

    private String planExpMidnightYn;
    private Double reservedAmount;
    private Long maxRenewalCount;
    private Double salesFee;
    private String atpBndlBktMultirecIns;
    private String publicityId;
    private String EndDate;

    private String recordInsertedBy;

    public Long getTariffPackageId() {
        return tariffPackageId;
    }

    public String getEndDate() {
		return EndDate;
	}

	public void setEndDate(String endDate) {
		EndDate = endDate;
	}

	public void setTariffPackageId(Long tariffPackageId) {
        this.tariffPackageId = tariffPackageId;
    }

    public String getTariffPackageDesc() {
        return tariffPackageDesc;
    }

    public void setTariffPackageDesc(String tariffPackageDesc) {
        this.tariffPackageDesc = tariffPackageDesc;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getIsCorporateYn() {
        return isCorporateYn;
    }

    public void setIsCorporateYn(String isCorporateYn) {
        this.isCorporateYn = isCorporateYn;
    }

    public String getTariffPackCategory() {
        return tariffPackCategory;
    }

    public void setTariffPackCategory(String tariffPackCategory) {
        this.tariffPackCategory = tariffPackCategory;
    }

    public Long getServicePackageId() {
        return servicePackageId;
    }

    public void setServicePackageId(Long servicePackageId) {
        this.servicePackageId = servicePackageId;
    }

    public String getTariffPlanType() {
        return tariffPlanType;
    }

    public void setTariffPlanType(String tariffPlanType) {
        this.tariffPlanType = tariffPlanType;
    }

    public String getChargeId() {
        return chargeId;
    }

    public void setChargeId(String chargeId) {
        this.chargeId = chargeId;
    }

    public Long getEffectiveStartOffset() {
        return effectiveStartOffset;
    }

    public void setEffectiveStartOffset(Long effectiveStartOffset) {
        this.effectiveStartOffset = effectiveStartOffset;
    }

    public Long getServiceDuration() {
        return serviceDuration;
    }

    public void setServiceDuration(Long serviceDuration) {
        this.serviceDuration = serviceDuration;
    }

    public Long getPriority() {
        return priority;
    }

    public void setPriority(Long priority) {
        this.priority = priority;
    }

    public Date getCreatedOrModifiedDate() {
        return createdOrModifiedDate;
    }

    public void setCreatedOrModifiedDate(Date createdOrModifiedDate) {
        this.createdOrModifiedDate = createdOrModifiedDate;
    }

    public Long getServicePlanId() {
        return servicePlanId;
    }

    public void setServicePlanId(Long servicePlanId) {
        this.servicePlanId = servicePlanId;
    }

    public Long getServiceType() {
        return serviceType;
    }

    public void setServiceType(Long serviceType) {
        this.serviceType = serviceType;
    }

    public String getRentalType() {
        return rentalType;
    }

    public void setRentalType(String rentalType) {
        this.rentalType = rentalType;
    }

    public Long getRentalPeriod() {
        return rentalPeriod;
    }

    public void setRentalPeriod(Long rentalPeriod) {
        this.rentalPeriod = rentalPeriod;
    }

    public Long getAlcsId() {
        return alcsId;
    }

    public void setAlcsId(Long alcsId) {
        this.alcsId = alcsId;
    }

    public Double getActivationFee() {
        return activationFee;
    }

    public void setActivationFee(Double activationFee) {
        this.activationFee = activationFee;
    }

    public Double getRentalFee() {
        return rentalFee;
    }

    public void setRentalFee(Double rentalFee) {
        this.rentalFee = rentalFee;
    }

    public Long getRentalFreeCycles() {
        return rentalFreeCycles;
    }

    public void setRentalFreeCycles(Long rentalFreeCycles) {
        this.rentalFreeCycles = rentalFreeCycles;
    }

    public String getProRatYn() {
        return proRatYn;
    }

    public void setProRatYn(String proRatYn) {
        this.proRatYn = proRatYn;
    }

    public String getAdvYn() {
        return advYn;
    }

    public void setAdvYn(String advYn) {
        this.advYn = advYn;
    }

    public Double getTax1() {
        return tax1;
    }

    public void setTax1(Double tax1) {
        this.tax1 = tax1;
    }

    public Double getTax2() {
        return tax2;
    }

    public void setTax2(Double tax2) {
        this.tax2 = tax2;
    }

    public Double getTax3() {
        return tax3;
    }

    public void setTax3(Double tax3) {
        this.tax3 = tax3;
    }

    public String getAddPackChargeYn() {
        return addPackChargeYn;
    }

    public void setAddPackChargeYn(String addPackChargeYn) {
        this.addPackChargeYn = addPackChargeYn;
    }

    public String getRentalDeductionInGrace() {
        return rentalDeductionInGrace;
    }

    public void setRentalDeductionInGrace(String rentalDeductionInGrace) {
        this.rentalDeductionInGrace = rentalDeductionInGrace;
    }

    public Double getDeactivationFee() {
        return deactivationFee;
    }

    public void setDeactivationFee(Double deactivationFee) {
        this.deactivationFee = deactivationFee;
    }

    public String getAutoRenewal() {
        return autoRenewal;
    }

    public void setAutoRenewal(String autoRenewal) {
        this.autoRenewal = autoRenewal;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public String getPlanExpMidnightYn() {
        return planExpMidnightYn;
    }

    public void setPlanExpMidnightYn(String planExpMidnightYn) {
        this.planExpMidnightYn = planExpMidnightYn;
    }

    public Double getReservedAmount() {
        return reservedAmount;
    }

    public void setReservedAmount(Double reservedAmount) {
        this.reservedAmount = reservedAmount;
    }

    public Long getMaxRenewalCount() {
        return maxRenewalCount;
    }

    public void setMaxRenewalCount(Long maxRenewalCount) {
        this.maxRenewalCount = maxRenewalCount;
    }

    public Double getSalesFee() {
        return salesFee;
    }

    public void setSalesFee(Double salesFee) {
        this.salesFee = salesFee;
    }

    public String getAtpBndlBktMultirecIns() {
        return atpBndlBktMultirecIns;
    }

    public void setAtpBndlBktMultirecIns(String atpBndlBktMultirecIns) {
        this.atpBndlBktMultirecIns = atpBndlBktMultirecIns;
    }

    public String getPublicityId() {
        return publicityId;
    }

    public void setPublicityId(String publicityId) {
        this.publicityId = publicityId;
    }

    public String getRecordInsertedBy() {
        return recordInsertedBy;
    }

    public void setRecordInsertedBy(String recordInsertedBy) {
        this.recordInsertedBy = recordInsertedBy;
    }

    public String getServicePackageDesc() {
        return servicePackageDesc;
    }

    public void setServicePackageDesc(String servicePackageDesc) {
        this.servicePackageDesc = servicePackageDesc;
    }

}