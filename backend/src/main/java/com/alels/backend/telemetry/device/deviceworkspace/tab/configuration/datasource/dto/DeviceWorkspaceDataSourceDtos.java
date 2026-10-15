package com.alels.backend.telemetry.device.deviceworkspace.tab.configuration.datasource.dto;

import java.util.List;
import java.util.Map;

public final class DeviceWorkspaceDataSourceDtos {
    private DeviceWorkspaceDataSourceDtos() {}
    public record SourceOption(String selector, String fieldCode, String label, String unit, String category,
                               String sourceIoId, String sourceProtocol, String dictionaryCode, String sourceName,
                               String lastValue, String lastReceivedAt, long sampleCount, boolean numericAvailable) {}
    public record Profile(Long id, String name, String rpmSource, String speedSource, String levelSource,
                          String consumptionSource, String odometerSource, boolean companyDefault,
                          String workspaceConfiguration, Map<String,String> unitPreferences) {}
    public record ProfileList(List<Profile> profiles, Long effectiveProfileId) {}
    public record SaveProfileRequest(String name, String rpmSource, String speedSource, String levelSource,
                                     String consumptionSource, String odometerSource, String workspaceConfiguration,
                                     Map<String,String> unitPreferences, List<Long> deviceIds, Boolean applyAll) {}
    public record ApplyProfileRequest(Long profileId, List<Long> deviceIds, Boolean applyAll) {}
}
