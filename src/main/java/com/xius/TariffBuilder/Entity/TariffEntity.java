

package com.xius.TariffBuilder.Entity;

public class TariffEntity {

    private Long networkId;
    private Long tariffPackageId;
    private String tariffPackageName;
    private String status;


    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public Long getTariffPackageId() {
        return tariffPackageId;
    }

    public void setTariffPackageId(Long tariffPackageId) {
        this.tariffPackageId = tariffPackageId;
    }

    public String getTariffPackageName() {
        return tariffPackageName;
    }

    public void setTariffPackageName(String tariffPackageName) {
        this.tariffPackageName = tariffPackageName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}