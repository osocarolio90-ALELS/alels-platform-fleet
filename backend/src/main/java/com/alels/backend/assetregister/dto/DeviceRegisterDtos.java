package com.alels.backend.assetregister.dto;

public final class DeviceRegisterDtos {
    private DeviceRegisterDtos() {}

    public record DeviceRegisterRow(
            Long id,
            Long companyId,
            String companyName,
            Long deviceBrandId,
            String deviceBrand,
            Long deviceModelId,
            String deviceModel,
            String imei,
            String gsmNumber,
            String tcpHost,
            Integer tcpPort,
            String protocolCode,
            String parserCode,
            String dictionaryCode,
            String status,
            String createdAt,
            String createdBy,
            String updatedAt
    ) {}

    public record DeviceRegisterRequest(
            Long companyId,
            Long deviceBrandId,
            Long deviceModelId,
            String imei,
            String gsmNumber,
            String tcpHost,
            Integer tcpPort,
            String registerStatus,
            String notes
    ) {}

    public record DeviceLookupOption(Long id, String label, String code, String extra) {}
}
