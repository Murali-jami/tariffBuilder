package com.xius.TariffBuilder.Dto;

public class ServicePackageRequest {

    private Long servicePackageId;
    private Long networkId;

    public Long getServicePackageId() {
        return servicePackageId;
    }

    public void setServicePackageId(Long servicePackageId) {
        this.servicePackageId = servicePackageId;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }
}