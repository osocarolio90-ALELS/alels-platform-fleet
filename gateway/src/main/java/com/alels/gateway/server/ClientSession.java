package com.alels.gateway.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import com.alels.gateway.command.CommandResponseHandler;
import com.alels.gateway.detector.ProtocolDetector;
import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.dictionary.AvlValueConverter;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.normalizer.AlelsJsonTelemetryNormalizer;
import com.alels.gateway.parser.AlelsJsonParser;
import com.alels.gateway.parser.ParserResult;
import com.alels.gateway.parser.TeltonikaCodec12ResponseParser;
import com.alels.gateway.parser.TeltonikaCodec8EParser;
import com.alels.gateway.parser.TeltonikaCodec8Parser;
import com.alels.gateway.parser.TeltonikaImeiParser;
import com.alels.gateway.publisher.TelemetryPublisher;
import com.alels.gateway.publisher.TelemetryPublisherFactory;
import com.alels.gateway.repository.CommandQueueRepository;
import com.alels.gateway.repository.CommandResponseRepository;
import com.alels.gateway.repository.DevicePresenceRepository;
import com.alels.gateway.repository.DeviceRepository;
import com.alels.gateway.repository.DeviceReceiveStatusRepository;
import com.alels.gateway.repository.DeviceStatusRepository;
import com.alels.gateway.repository.TcpLogRepository;
import com.alels.gateway.service.DeviceAutoProvisioningService;
import com.alels.gateway.service.DeviceModelResolver;
import com.alels.gateway.service.ProtocolRegistryResolver;
import com.alels.gateway.service.RawPacketService;
import com.alels.gateway.session.DeviceChannelTracker;
import com.alels.gateway.session.DeviceSessionManager;
import com.alels.gateway.util.HexUtil;

public class ClientSession implements Runnable {

    private final Socket socket;

    private String boundImei = null;
    private String sessionImei = null;
    private ChannelType sessionChannel = null;

    private String lastTeltonikaCommandName = null;

    private static final String FALLBACK_DEVICE_MODEL = "fmc650";

    private static final TelemetryPublisher telemetryPublisher =
            TelemetryPublisherFactory.create();

    public ClientSession(Socket socket) {
        this.socket = socket;
    }

    private String resolveDictionaryCode(String imei) {
        if (imei == null || imei.isBlank()) {
            return FALLBACK_DEVICE_MODEL;
        }

        return DeviceModelResolver.resolveDictionaryCode(imei);
    }

    private void upsertDevice(
            String imei,
            ChannelType channel,
            ProtocolType protocol
    ) {
        String modelCode =
                DeviceModelResolver.resolveModelCode(imei);

        DeviceRepository.upsert(
                imei,
                modelCode,
                channel.name(),
                protocol.name()
        );
    }

    private void ensureDeviceProvisioned(
            String imei,
            ProtocolType protocol
    ) {
        if (imei == null || imei.isBlank()) {
            return;
        }

        if (DeviceModelResolver.resolve(imei) != null) {
            return;
        }

        DeviceAutoProvisioningService.autoProvision(
                imei,
                protocol != null ? protocol.name() : null
        );

        DeviceModelResolver.clearCache();

        DeviceModelResolver.resolve(imei);
    }

    private boolean isProtocolAllowed(ProtocolType type) {

        if (type == null || type == ProtocolType.UNKNOWN) {
            return true;
        }

        boolean active =
                ProtocolRegistryResolver.isActive(type);

        if (!active) {
            System.out.println(
                    "[PROTOCOL REGISTRY] REJECT protocol="
                            + type.name()
                            + " status=NOT_ACTIVE"
            );

            TcpLogRepository.insert(
                    sessionImei,
                    socket.getRemoteSocketAddress().toString(),
                    sessionChannel != null ? sessionChannel.name() : null,
                    type.name(),
                    "PROTOCOL_NOT_ACTIVE",
                    "Protocol is not ACTIVE in protocol_registry",
                    null,
                    null
            );
        }

        return active;
    }

    private void sendAlelsAck(OutputStream out, String imei, String ackType) throws Exception {
        out.write("01\n".getBytes(StandardCharsets.UTF_8));
        out.flush();

        System.out.println("[ACK] " + ackType + " ACK=01 imei=" + imei);

        TcpLogRepository.insert(
                imei,
                socket.getRemoteSocketAddress().toString(),
                ChannelType.WIFI.name(),
                ProtocolType.ALELS_JSON.name(),
                "ACK_SENT",
                ackType + " ACK sent",
                null,
                3
        );
    }

    @Override
    public void run() {
        System.out.println("[CLIENT CONNECTED] " + socket.getRemoteSocketAddress());

        TcpLogRepository.insert(
                null,
                socket.getRemoteSocketAddress().toString(),
                null,
                null,
                "CLIENT_CONNECTED",
                "Client connected to TCP gateway",
                null,
                null
        );

        try (
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream()
        ) {
            byte[] buffer = new byte[8192];
            int len;

            while ((len = in.read(buffer)) != -1) {
                byte[] packet = Arrays.copyOf(buffer, len);
                ProtocolType type = ProtocolDetector.detect(packet);

                System.out.println("[PACKET] " + socket.getRemoteSocketAddress()
                        + " protocol=" + type
                        + " bytes=" + len);

                if (!isProtocolAllowed(type)) {
                    continue;
                }

                TcpLogRepository.insert(
                        sessionImei,
                        socket.getRemoteSocketAddress().toString(),
                        sessionChannel != null ? sessionChannel.name() : null,
                        type.name(),
                        "PACKET_RECEIVED",
                        "Packet received from device",
                        len,
                        null
                );

                switch (type) {
                    case ALELS_JSON:
                        handleAlelsJson(packet, out);
                        break;

                    case TELTONIKA_IMEI:
                        handleTeltonikaImei(packet, out);
                        break;

                    case TELTONIKA_CODEC8:
                        handleTeltonikaCodec8(packet, out);
                        break;

                    case TELTONIKA_CODEC8E:
                        handleTeltonikaCodec8E(packet, out);
                        break;

                    case TELTONIKA_CODEC12_RESPONSE:
                        handleTeltonikaCodec12Response(packet);
                        break;

                    default:
                        if (isAlelsHeartbeatPacket(packet)) {
                            handleAlelsHeartbeatRaw(packet, out);
                            break;
                        }

                        System.out.println("[WARN] Unknown protocol from " + socket.getRemoteSocketAddress());

                        TcpLogRepository.insert(
                                sessionImei,
                                socket.getRemoteSocketAddress().toString(),
                                sessionChannel != null ? sessionChannel.name() : null,
                                ProtocolType.UNKNOWN.name(),
                                "UNKNOWN_PACKET",
                                "Unknown protocol packet received",
                                len,
                                null
                        );
                        break;
                }
            }

        } catch (Exception e) {
            System.err.println("[SESSION ERROR] " + socket.getRemoteSocketAddress() + " " + e.getMessage());

            TcpLogRepository.insert(
                    sessionImei,
                    socket.getRemoteSocketAddress().toString(),
                    sessionChannel != null ? sessionChannel.name() : null,
                    null,
                    "SOCKET_ERROR",
                    e.getMessage(),
                    null,
                    null
            );

        } finally {
            DeviceStatusRepository.markChannelOffline(
                    sessionImei,
                    sessionChannel != null ? sessionChannel.name() : null
            );

            TcpLogRepository.insert(
                    sessionImei,
                    socket.getRemoteSocketAddress().toString(),
                    sessionChannel != null ? sessionChannel.name() : null,
                    null,
                    "CLIENT_DISCONNECTED",
                    "Client disconnected from TCP gateway",
                    null,
                    null
            );

            closeSocket();
            System.out.println("[CLIENT DISCONNECTED] " + socket.getRemoteSocketAddress());
        }
    }

    private boolean isAlelsHeartbeatPacket(byte[] packet) {
        String jsonText = new String(packet, StandardCharsets.UTF_8).trim();

        if (!jsonText.startsWith("{")) {
            return false;
        }

        return jsonText.contains("\"T\":\"hb\"")
                || jsonText.contains("\"T\":\"heart\"")
                || jsonText.contains("\"T\":\"ping\"");
    }

    private String extractJsonStringValue(String jsonText, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = jsonText.indexOf(pattern);

        if (start < 0) {
            return null;
        }

        start += pattern.length();
        int end = jsonText.indexOf("\"", start);

        if (end < 0) {
            return null;
        }

        String value = jsonText.substring(start, end).trim();
        return value.isBlank() ? null : value;
    }

    private void handleAlelsHeartbeatRaw(byte[] packet, OutputStream out) throws Exception {
        String jsonText = new String(packet, StandardCharsets.UTF_8).trim();

        String imei = extractJsonStringValue(jsonText, "A");
        if (imei == null) {
            imei = extractJsonStringValue(jsonText, "imei");
        }
        if (imei == null) {
            imei = sessionImei;
        }
        if (imei == null) {
            imei = "UNKNOWN";
        }

        this.sessionImei = imei;
        this.sessionChannel = ChannelType.WIFI;

        if (!DeviceReceiveStatusRepository.isReceiveAllowed(imei)) {
            System.out.println("[DEVICE RECEIVE] REJECT imei=" + imei + " status=SUSPENDED");
            return;
        }

        ensureDeviceProvisioned(
                imei,
                ProtocolType.ALELS_JSON
        );

        RawPacketService.logRawPacket(
                imei,
                ProtocolType.ALELS_JSON,
                ChannelType.WIFI,
                packet,
                socket.getRemoteSocketAddress().toString()
        );

        sendAlelsAck(out, imei, "ALELS HB");

        DeviceSessionManager.registerOrUpdate(
                imei,
                ChannelType.WIFI,
                ProtocolType.ALELS_JSON,
                socket
        );

        DeviceChannelTracker.updateChannel(
                imei,
                ChannelType.WIFI,
                ProtocolType.ALELS_JSON
        );

        upsertDevice(
                imei,
                ChannelType.WIFI,
                ProtocolType.ALELS_JSON
        );

        DeviceStatusRepository.markChannelOnline(
                imei,
                ChannelType.WIFI.name(),
                ProtocolType.ALELS_JSON.name()
        );

        DevicePresenceRepository.markPresentOnData(imei);

        System.out.println("[HEARTBEAT] ALELS HB imei=" + imei
                + " remote=" + socket.getRemoteSocketAddress());

        DeviceSessionManager.printSessions();
    }

    private void handleAlelsJson(byte[] packet, OutputStream out) throws Exception {
        AlelsJsonParser parser = new AlelsJsonParser();
        ParserResult result = parser.parse(packet);

        if (!result.isValid()) {
            System.out.println("[ALELS JSON] Invalid packet");
            return;
        }

        this.sessionImei = result.getImei();
        this.sessionChannel = ChannelType.WIFI;

        if (!DeviceReceiveStatusRepository.isReceiveAllowed(result.getImei())) {
            System.out.println("[DEVICE RECEIVE] REJECT imei=" + result.getImei() + " status=SUSPENDED");
            return;
        }

        ensureDeviceProvisioned(
                result.getImei(),
                ProtocolType.ALELS_JSON
        );

        String dictionaryCode =
                resolveDictionaryCode(result.getImei());

        String jsonText = new String(packet, StandardCharsets.UTF_8).trim();
        boolean isResponse = jsonText.contains("\"T\":\"resp\"");
        boolean isId = jsonText.contains("\"T\":\"id\"");
        boolean isHeartbeat = jsonText.contains("\"T\":\"hb\"")
                || jsonText.contains("\"T\":\"heart\"")
                || jsonText.contains("\"T\":\"ping\"");

        String ackType = "ALELS JSON";
        if (isResponse) {
            ackType = "ALELS RESP";
        } else if (isId) {
            ackType = "ALELS ID";
        } else if (isHeartbeat) {
            ackType = "ALELS HB";
        }

        Long rawPacketId = RawPacketService.logRawPacket(
                result.getImei(),
                ProtocolType.ALELS_JSON,
                ChannelType.WIFI,
                packet,
                socket.getRemoteSocketAddress().toString()
        );

        DeviceSessionManager.registerOrUpdate(
                result.getImei(),
                ChannelType.WIFI,
                ProtocolType.ALELS_JSON,
                socket
        );

        DeviceChannelTracker.updateChannel(
                result.getImei(),
                ChannelType.WIFI,
                ProtocolType.ALELS_JSON
        );

        upsertDevice(
                result.getImei(),
                ChannelType.WIFI,
                ProtocolType.ALELS_JSON
        );

        DeviceStatusRepository.markChannelOnline(
                result.getImei(),
                ChannelType.WIFI.name(),
                ProtocolType.ALELS_JSON.name()
        );

        DevicePresenceRepository.markPresentOnData(result.getImei());

        if (isResponse) {
            CommandResponseHandler.handleAlelsJsonResponse(packet);
            sendAlelsAck(out, result.getImei(), ackType);
            DeviceSessionManager.printSessions();
            return;
        }

        if (isId || isHeartbeat) {
            sendAlelsAck(out, result.getImei(), ackType);
            DeviceSessionManager.printSessions();
            return;
        }

        TelemetryData telemetryData = AlelsJsonTelemetryNormalizer.normalize(packet);

        if (telemetryData != null) {
            telemetryData.print();

            telemetryPublisher.publish(
                    rawPacketId,
                    telemetryData,
                    ProtocolType.ALELS_JSON.name(),
                    ChannelType.WIFI.name(),
                    dictionaryCode,
                    "ALELS_JSON"
            ).toCompletableFuture().join();

            printDictionaryIo(telemetryData, dictionaryCode);
        }

        sendAlelsAck(out, result.getImei(), ackType);

        DeviceSessionManager.printSessions();

    }

    private void handleTeltonikaImei(byte[] packet, OutputStream out) throws Exception {
        TeltonikaImeiParser parser = new TeltonikaImeiParser();
        ParserResult result = parser.parse(packet);

        if (!result.isValid()) {
            out.write(new byte[]{0x00});
            out.flush();
            return;
        }

        this.boundImei = result.getImei();
        this.sessionImei = result.getImei();
        this.sessionChannel = ChannelType.GSM;

        if (!DeviceReceiveStatusRepository.isReceiveAllowed(result.getImei())) {
            out.write(new byte[]{0x00});
            out.flush();
            System.out.println("[DEVICE RECEIVE] REJECT imei=" + result.getImei() + " status=SUSPENDED");
            return;
        }

        ensureDeviceProvisioned(
                boundImei,
                ProtocolType.TELTONIKA_IMEI
        );

        System.out.println("[IMEI BIND] socket=" + socket.getRemoteSocketAddress()
                + " imei=" + boundImei);

        RawPacketService.logRawPacket(
                boundImei,
                ProtocolType.TELTONIKA_IMEI,
                ChannelType.GSM,
                packet,
                socket.getRemoteSocketAddress().toString()
        );

        DeviceSessionManager.registerOrUpdate(
                boundImei,
                ChannelType.GSM,
                ProtocolType.TELTONIKA_IMEI,
                socket
        );

        DeviceChannelTracker.updateChannel(
                boundImei,
                ChannelType.GSM,
                ProtocolType.TELTONIKA_IMEI
        );

        upsertDevice(
                boundImei,
                ChannelType.GSM,
                ProtocolType.TELTONIKA_IMEI
        );

        DeviceStatusRepository.markChannelOnline(
                boundImei,
                ChannelType.GSM.name(),
                ProtocolType.TELTONIKA_IMEI.name()
        );

        DevicePresenceRepository.markPresentOnData(boundImei);

        out.write(new byte[]{0x01});
        out.flush();

        System.out.println("[IMEI] " + boundImei);
        System.out.println("[ACK] TELTONIKA IMEI ACK=01");

        TcpLogRepository.insert(
                boundImei,
                socket.getRemoteSocketAddress().toString(),
                ChannelType.GSM.name(),
                ProtocolType.TELTONIKA_IMEI.name(),
                "IMEI_RECEIVED",
                "Teltonika IMEI received and accepted",
                packet.length,
                1
        );

        TcpLogRepository.insert(
                boundImei,
                socket.getRemoteSocketAddress().toString(),
                ChannelType.GSM.name(),
                ProtocolType.TELTONIKA_IMEI.name(),
                "ACK_SENT",
                "Teltonika IMEI ACK sent",
                null,
                1
        );

        DeviceSessionManager.printSessions();

        lastTeltonikaCommandName = "getinfo";
    }

    private void handleTeltonikaCodec8(byte[] packet, OutputStream out) throws Exception {
        TeltonikaCodec8Parser parser = new TeltonikaCodec8Parser();
        ParserResult result = parser.parse(packet);

        int acceptedRecords = result.isValid() ? result.getRecordCount() : 0;

        if (!DeviceReceiveStatusRepository.isReceiveAllowed(boundImei)) {
            out.write(ByteBuffer.allocate(4).putInt(0).array());
            out.flush();
            System.out.println("[DEVICE RECEIVE] REJECT imei=" + boundImei + " status=SUSPENDED");
            return;
        }

        ensureDeviceProvisioned(
                boundImei,
                ProtocolType.TELTONIKA_CODEC8
        );

        String dictionaryCode =
                resolveDictionaryCode(boundImei);

        Long rawPacketId = RawPacketService.logRawPacket(
                boundImei,
                ProtocolType.TELTONIKA_CODEC8,
                ChannelType.GSM,
                packet,
                socket.getRemoteSocketAddress().toString()
        );

        if (result.hasTelemetry()) {
            result.getTelemetryList().forEach(t -> {
                t.setImei(boundImei);
                t.print();

                telemetryPublisher.publish(
                        rawPacketId,
                        t,
                        ProtocolType.TELTONIKA_CODEC8.name(),
                        ChannelType.GSM.name(),
                        dictionaryCode,
                        "CODEC8"
                ).toCompletableFuture().join();

                printDictionaryIo(t, dictionaryCode);
            });
        }

        if (boundImei != null && !boundImei.isBlank()) {
            DeviceSessionManager.registerOrUpdate(
                    boundImei,
                    ChannelType.GSM,
                    ProtocolType.TELTONIKA_CODEC8,
                    socket
            );

            DeviceChannelTracker.updateChannel(
                    boundImei,
                    ChannelType.GSM,
                    ProtocolType.TELTONIKA_CODEC8
            );

            upsertDevice(
                    boundImei,
                    ChannelType.GSM,
                    ProtocolType.TELTONIKA_CODEC8
            );

            DeviceStatusRepository.markChannelOnline(
                    boundImei,
                    ChannelType.GSM.name(),
                    ProtocolType.TELTONIKA_CODEC8.name()
            );

            DevicePresenceRepository.markPresentOnData(boundImei);

            System.out.println("[TELTONIKA DATA] imei=" + boundImei
                    + " protocol=TELTONIKA_CODEC8"
                    + " records=" + acceptedRecords
                    + " dictionary=" + dictionaryCode);
        }

        byte[] ack = ByteBuffer.allocate(4)
                .putInt(acceptedRecords)
                .array();

        out.write(ack);
        out.flush();

        System.out.println("[ACK] TELTONIKA CODEC8 ACK=" + acceptedRecords);

        TcpLogRepository.insert(
                boundImei,
                socket.getRemoteSocketAddress().toString(),
                ChannelType.GSM.name(),
                ProtocolType.TELTONIKA_CODEC8.name(),
                "ACK_SENT",
                "Teltonika Codec8 AVL ACK sent",
                null,
                ack.length
        );
    }

    private void handleTeltonikaCodec8E(byte[] packet, OutputStream out) throws Exception {
        TeltonikaCodec8EParser parser = new TeltonikaCodec8EParser();
        ParserResult result = parser.parse(packet);

        int acceptedRecords = result.isValid() ? result.getRecordCount() : 0;

        if (!DeviceReceiveStatusRepository.isReceiveAllowed(boundImei)) {
            out.write(ByteBuffer.allocate(4).putInt(0).array());
            out.flush();
            System.out.println("[DEVICE RECEIVE] REJECT imei=" + boundImei + " status=SUSPENDED");
            return;
        }

        ensureDeviceProvisioned(
                boundImei,
                ProtocolType.TELTONIKA_CODEC8E
        );

        String dictionaryCode =
                resolveDictionaryCode(boundImei);

        Long rawPacketId = RawPacketService.logRawPacket(
                boundImei,
                ProtocolType.TELTONIKA_CODEC8E,
                ChannelType.GSM,
                packet,
                socket.getRemoteSocketAddress().toString()
        );

        if (result.hasTelemetry()) {
            result.getTelemetryList().forEach(t -> {
                t.setImei(boundImei);
                t.print();

                telemetryPublisher.publish(
                        rawPacketId,
                        t,
                        ProtocolType.TELTONIKA_CODEC8E.name(),
                        ChannelType.GSM.name(),
                        dictionaryCode,
                        "CODEC8E"
                ).toCompletableFuture().join();

                printDictionaryIo(t, dictionaryCode);
            });
        }

        if (boundImei != null && !boundImei.isBlank()) {
            DeviceSessionManager.registerOrUpdate(
                    boundImei,
                    ChannelType.GSM,
                    ProtocolType.TELTONIKA_CODEC8E,
                    socket
            );

            DeviceChannelTracker.updateChannel(
                    boundImei,
                    ChannelType.GSM,
                    ProtocolType.TELTONIKA_CODEC8E
            );

            upsertDevice(
                    boundImei,
                    ChannelType.GSM,
                    ProtocolType.TELTONIKA_CODEC8E
            );

            DeviceStatusRepository.markChannelOnline(
                    boundImei,
                    ChannelType.GSM.name(),
                    ProtocolType.TELTONIKA_CODEC8E.name()
            );

            DevicePresenceRepository.markPresentOnData(boundImei);

            System.out.println("[TELTONIKA DATA] imei=" + boundImei
                    + " protocol=TELTONIKA_CODEC8E"
                    + " records=" + acceptedRecords
                    + " dictionary=" + dictionaryCode);
        }

        byte[] ack = ByteBuffer.allocate(4)
                .putInt(acceptedRecords)
                .array();

        out.write(ack);
        out.flush();

        System.out.println("[ACK] TELTONIKA CODEC8E ACK=" + acceptedRecords);

        TcpLogRepository.insert(
                boundImei,
                socket.getRemoteSocketAddress().toString(),
                ChannelType.GSM.name(),
                ProtocolType.TELTONIKA_CODEC8E.name(),
                "ACK_SENT",
                "Teltonika Codec8E AVL ACK sent",
                null,
                ack.length
        );
    }

    private void handleTeltonikaCodec12Response(byte[] packet) throws Exception {
        TeltonikaCodec12ResponseParser parser = new TeltonikaCodec12ResponseParser();
        ParserResult result = parser.parse(packet);

        ensureDeviceProvisioned(
                boundImei,
                ProtocolType.TELTONIKA_CODEC12_RESPONSE
        );

        RawPacketService.logRawPacket(
                boundImei,
                ProtocolType.TELTONIKA_CODEC12_RESPONSE,
                ChannelType.GSM,
                packet,
                socket.getRemoteSocketAddress().toString()
        );

        DeviceStatusRepository.markChannelOnline(
                boundImei,
                ChannelType.GSM.name(),
                ProtocolType.TELTONIKA_CODEC12_RESPONSE.name()
        );

        DevicePresenceRepository.markPresentOnData(boundImei);

        String commandName = lastTeltonikaCommandName != null
                ? lastTeltonikaCommandName
                : "UNKNOWN_CODEC12_COMMAND";

        String rawHex = HexUtil.toHex(packet);

        if (result.isValid()) {
            System.out.println("[COMMAND RESPONSE] imei=" + boundImei
                    + " protocol=TELTONIKA_CODEC12"
                    + " command=" + commandName
                    + " records=" + result.getRecordCount());

            CommandResponseRepository.insertResponse(
                    boundImei,
                    commandName,
                    "OK",
                    "Teltonika Codec12 response received",
                    rawHex
            );

            CommandQueueRepository.markLatestAcked(
                    boundImei,
                    commandName.startsWith("UNKNOWN")
                            ? null
                            : commandName
            );

            TcpLogRepository.insert(
                    boundImei,
                    socket.getRemoteSocketAddress().toString(),
                    ChannelType.GSM.name(),
                    ProtocolType.TELTONIKA_CODEC12_RESPONSE.name(),
                    "COMMAND_RESPONSE",
                    "Valid Teltonika Codec12 response received",
                    packet.length,
                    null
            );
        } else {
            System.out.println("[COMMAND RESPONSE] invalid Codec12 response imei=" + boundImei);

            CommandResponseRepository.insertResponse(
                    boundImei,
                    commandName,
                    "FAILED",
                    "Invalid Teltonika Codec12 response",
                    rawHex
            );

            TcpLogRepository.insert(
                    boundImei,
                    socket.getRemoteSocketAddress().toString(),
                    ChannelType.GSM.name(),
                    ProtocolType.TELTONIKA_CODEC12_RESPONSE.name(),
                    "COMMAND_RESPONSE_INVALID",
                    "Invalid Teltonika Codec12 response received",
                    packet.length,
                    null
            );
        }
    }

    private void printDictionaryIo(TelemetryData telemetryData, String deviceModel) {
        try {
            Map<String, Object> converted = AvlValueConverter.convert(
                    telemetryData.getIoData(),
                    deviceModel
            );

            System.out.println("========== DICTIONARY IO ==========");
            System.out.println("MODEL    : " + deviceModel.toUpperCase());
            System.out.println("CONVERTED: " + converted);
            System.out.println("===================================");

        } catch (Exception e) {
            System.err.println("[DICTIONARY IO ERROR] " + e.getMessage());
        }
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
