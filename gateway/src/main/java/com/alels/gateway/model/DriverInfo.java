package com.alels.gateway.model;

public class DriverInfo {

    private Long driverId;
    private String driverName;
    private String driverRfid;

    public DriverInfo() {
    }

    public DriverInfo(
            Long driverId,
            String driverName,
            String driverRfid
    ) {
        this.driverId = driverId;
        this.driverName = driverName;
        this.driverRfid = driverRfid;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverRfid() {
        return driverRfid;
    }

    public void setDriverRfid(String driverRfid) {
        this.driverRfid = driverRfid;
    }
}