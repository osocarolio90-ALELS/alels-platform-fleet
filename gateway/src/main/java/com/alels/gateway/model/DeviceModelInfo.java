package com.alels.gateway.model;

public class DeviceModelInfo {

    private Long modelId;

    private String brandCode;
    private String brandName;

    private String modelCode;
    private String modelName;

    private String protocolCode;
    private String parserCode;
    private String dictionaryCode;

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public void setBrandCode(String brandCode) {
        this.brandCode = brandCode;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getProtocolCode() {
        return protocolCode;
    }

    public void setProtocolCode(String protocolCode) {
        this.protocolCode = protocolCode;
    }

    public String getParserCode() {
        return parserCode;
    }

    public void setParserCode(String parserCode) {
        this.parserCode = parserCode;
    }

    public String getDictionaryCode() {
        return dictionaryCode;
    }

    public void setDictionaryCode(String dictionaryCode) {
        this.dictionaryCode = dictionaryCode;
    }

    public boolean hasDictionary() {
        return dictionaryCode != null && !dictionaryCode.isBlank();
    }

    public boolean isTeltonikaAutoParser() {
        return "TELTONIKA_AUTO".equalsIgnoreCase(parserCode);
    }

    public boolean isAlelsJsonParser() {
        return "ALELS_JSON".equalsIgnoreCase(parserCode);
    }

    public String getSafeDictionaryCode(String fallback) {
        if (hasDictionary()) {
            return dictionaryCode;
        }

        return fallback;
    }

    @Override
    public String toString() {
        return "DeviceModelInfo{" +
                "modelId=" + modelId +
                ", brandCode='" + brandCode + '\'' +
                ", brandName='" + brandName + '\'' +
                ", modelCode='" + modelCode + '\'' +
                ", modelName='" + modelName + '\'' +
                ", protocolCode='" + protocolCode + '\'' +
                ", parserCode='" + parserCode + '\'' +
                ", dictionaryCode='" + dictionaryCode + '\'' +
                '}';
    }
}