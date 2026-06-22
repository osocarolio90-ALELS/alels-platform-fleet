package com.alels.gateway.parser;

import java.util.ArrayList;
import java.util.List;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.model.TelemetryData;

public class ParserResult {
    private String imei;
    private ProtocolType protocolType;
    private int recordCount;
    private boolean valid;

    private List<TelemetryData> telemetryList = new ArrayList<>();

    public ParserResult(String imei, ProtocolType protocolType, int recordCount, boolean valid) {
        this.imei = imei;
        this.protocolType = protocolType;
        this.recordCount = recordCount;
        this.valid = valid;
    }

    public ParserResult(
            String imei,
            ProtocolType protocolType,
            int recordCount,
            boolean valid,
            List<TelemetryData> telemetryList
    ) {
        this.imei = imei;
        this.protocolType = protocolType;
        this.recordCount = recordCount;
        this.valid = valid;

        if (telemetryList != null) {
            this.telemetryList = telemetryList;
        }
    }

    public String getImei() {
        return imei;
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public boolean isValid() {
        return valid;
    }

    public List<TelemetryData> getTelemetryList() {
        return telemetryList;
    }

    public boolean hasTelemetry() {
        return telemetryList != null && !telemetryList.isEmpty();
    }
}