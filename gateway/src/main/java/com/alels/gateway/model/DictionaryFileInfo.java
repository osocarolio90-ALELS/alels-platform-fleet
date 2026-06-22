package com.alels.gateway.model;

public class DictionaryFileInfo {

    private Long dictionaryRegistryId;
    private Long deviceModelId;

    private String dictionaryCode;
    private String dictionaryFile;

    public Long getDictionaryRegistryId() {
        return dictionaryRegistryId;
    }

    public void setDictionaryRegistryId(Long dictionaryRegistryId) {
        this.dictionaryRegistryId = dictionaryRegistryId;
    }

    public Long getDeviceModelId() {
        return deviceModelId;
    }

    public void setDeviceModelId(Long deviceModelId) {
        this.deviceModelId = deviceModelId;
    }

    public String getDictionaryCode() {
        return dictionaryCode;
    }

    public void setDictionaryCode(String dictionaryCode) {
        this.dictionaryCode = dictionaryCode;
    }

    public String getDictionaryFile() {
        return dictionaryFile;
    }

    public void setDictionaryFile(String dictionaryFile) {
        this.dictionaryFile = dictionaryFile;
    }
}