package com.alels.gateway.admission.model;

public record DeviceAdmission(
        String imei,
        boolean receiveAllowed,
        String dictionaryCode
) {
}
