package com.alels.backend.telemetry.device.deviceworkspace.tab.logsmessage.dto;

import java.util.List;

public final class DeviceWorkspaceLogsMessageDtos {
    private DeviceWorkspaceLogsMessageDtos() {}
    public record Page<T>(List<T> rows, int page, int size, long totalRows, int totalPages) {}
    public record TrafficRow(Long id, String timestamp, String eventType, String title, String imei,
                             Long receivedBytes, Long sentBytes, String remoteAddress, String transport,
                             String source, String protocol, String status, String details) {}
    public record PacketRow(Long id, String timestamp, String direction, long sizeBytes,
                            String transport, String protocol) {}
    public record PacketDetail(Long id, String timestamp, String direction, long sizeBytes,
                               String transport, String protocol, String remoteAddress,
                               String rawHex, String rawText) {}
    public record PacketSummary(long receivedBytes, long transmittedBytes, long totalBytes,
                                long rxPacketCount, long txPacketCount) {}
}
