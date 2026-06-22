package com.alels.gateway.model;

public class DictionaryImportJobInfo {

    private Long id;
    private Long dictionaryRegistryId;
    private Long deviceModelId;

    private String dictionaryCode;
    private String dictionaryFile;

    private String importStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getImportStatus() {
        return importStatus;
    }

    public void setImportStatus(String importStatus) {
        this.importStatus = importStatus;
    }
}