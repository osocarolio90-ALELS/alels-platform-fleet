package com.alels.gateway.model;

public class ProtocolInfo {

    private Long id;

    private String protocolCode;
    private String protocolName;
    private String brandCode;
    private String protocolFamily;
    private String detectorCode;
    private String parserCode;
    private String transportType;
    private String direction;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProtocolCode() {
        return protocolCode;
    }

    public void setProtocolCode(String protocolCode) {
        this.protocolCode = protocolCode;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public void setProtocolName(String protocolName) {
        this.protocolName = protocolName;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public void setBrandCode(String brandCode) {
        this.brandCode = brandCode;
    }

    public String getProtocolFamily() {
        return protocolFamily;
    }

    public void setProtocolFamily(String protocolFamily) {
        this.protocolFamily = protocolFamily;
    }

    public String getDetectorCode() {
        return detectorCode;
    }

    public void setDetectorCode(String detectorCode) {
        this.detectorCode = detectorCode;
    }

    public String getParserCode() {
        return parserCode;
    }

    public void setParserCode(String parserCode) {
        this.parserCode = parserCode;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isPlanned() {
        return "PLANNED".equalsIgnoreCase(status);
    }

    public void setStatus(String status) {
        this.status = status;
    }
}