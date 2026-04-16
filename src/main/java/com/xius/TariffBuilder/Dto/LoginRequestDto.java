package com.xius.TariffBuilder.Dto;


public class LoginRequestDto {

    private String networkLoginName;
    private String loginId;
    private String password;

    public String getNetworkLoginName() {
        return networkLoginName;
    }

    public void setNetworkLoginName(String networkLoginName) {
        this.networkLoginName = networkLoginName;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}