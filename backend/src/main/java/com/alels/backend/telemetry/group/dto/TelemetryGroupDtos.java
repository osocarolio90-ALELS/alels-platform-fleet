package com.alels.backend.telemetry.group.dto;

import java.util.List;

public final class TelemetryGroupDtos {
    private TelemetryGroupDtos() {}
    public record GroupRow(Long id, Long companyId, String company, String groupName, String description,
            int deviceCount, String createdBy, String createdAt, String deletedBy, String deletedAt,
            String deletePermanentAt, List<Long> deviceIds) {}
    public record GroupRequest(String groupName, String description, List<Long> deviceIds) {}
    public record DeviceOption(Long id, String imei, String vehicle, Long companyId, String company, String deviceModel) {}
    public record LogRow(String time, String action, String company, String group, String user, int deviceCount, String details) {}
}
