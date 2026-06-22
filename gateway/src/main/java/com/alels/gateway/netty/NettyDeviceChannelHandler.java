package com.alels.gateway.netty;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
import com.alels.gateway.server.ChannelType;
import com.alels.gateway.service.DeviceAutoProvisioningService;
import com.alels.gateway.service.DeviceModelResolver;
import com.alels.gateway.service.ProtocolRegistryResolver;
import com.alels.gateway.service.RawPacketService;
import com.alels.gateway.session.DeviceChannelTracker;
import com.alels.gateway.session.DeviceSessionManager;
import com.alels.gateway.util.HexUtil;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class NettyDeviceChannelHandler extends SimpleChannelInboundHandler<byte[]> {

    private String boundImei = null;
    private String sessionImei = null;
    private ChannelType sessionChannel = null;

    private String lastAlelsCommandName = null;
    private String lastTeltonikaCommandName = null;

    private static final String FALLBACK_DEVICE_MODEL = "fmc650";

    private static final TelemetryPublisher telemetryPublisher =
            TelemetryPublisherFactory.create();

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("[CLIENT CONNECTED] " + remoteAddress(ctx));

        TcpLogRepository.insert(
                null,
                remoteAddress(ctx),
                null,
                null,
                "CLIENT_CONNECTED",
                "Client connected to TCP gateway",
                null,
                null
        );
    }

    @Override
    protected void channelRead0(
            ChannelHandlerContext ctx,
            byte[] packet
    ) throws Exception {
        ProtocolType type =
                ProtocolDetector.detect(packet);

        System.out.println("[PACKET] " + remoteAddress(ctx)
                + " protocol=" + type
                + " bytes=" + packet.length);

        if (!isProtocolAllowed(ctx, type)) {
            return;
        }

        TcpLogRepository.insert(
                sessionImei,
                remoteAddress(ctx),
                sessionChannel != null ? sessionChannel.name() : null,
                type.name(),
                "PACKET_RECEIVED",
                "Packet received from device",
                packet.length,
                null
        );

        switch (type) {
            case ALELS_JSON:
                handleAlelsJson(ctx, packet);
                break;

            case TELTONIKA_IMEI:
                handleTeltonikaImei(ctx, packet);
                break;

            case TELTONIKA_CODEC8:
                handleTeltonikaCodec8(ctx, packet);
                break;

            case TELTONIKA_CODEC8E:
                handleTeltonikaCodec8E(ctx, packet);
                break;

            case TELTONIKA_CODEC12_RESPONSE:
                handleTeltonikaCodec12Response(ctx, packet);
                break;

            default:
                if (isAlelsHeartbeatPacket(packet)) {
                    handleAlelsHeartbeatRaw(ctx, packet);
                    break;
                }

                System.out.println("[WARN] Unknown protocol from " + remoteAddress(ctx));

                TcpLogRepository.insert(
                        sessionImei,
                        remoteAddress(ctx),
                        sessionChannel != null ? sessionChannel.name() : null,
                        ProtocolType.UNKNOWN.name(),
                        "UNKNOWN_PACKET",
                        "Unknown protocol packet received",
                        packet.length,
                        null
                );
                break;
        }
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx,
            Throwable cause
    ) {
        System.err.println("[SESSION ERROR] " + remoteAddress(ctx) + " " + cause.getMessage());

        TcpLogRepository.insert(
                sessionImei,
                remoteAddress(ctx),
                sessionChannel != null ? sessionChannel.name() : null,
                null,
                "SOCKET_ERROR",
                cause.getMessage(),
                null,
                null
        );

        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        DeviceSessionRegistry.markChannelClosed(
                sessionImei,
                sessionChannel
        );

        TcpLogRepository.insert(
                sessionImei,
                remoteAddress(ctx),
                sessionChannel != null ? sessionChannel.name() : null,
                null,
                "CLIENT_DISCONNECTED",
                "Client disconnected from TCP gateway",
                null,
                null
        );

        System.out.println("[CLIENT DISCONNECTED] " + remoteAddress(ctx));
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

    private boolean isProtocolAllowed(
            ChannelHandlerContext ctx,
            ProtocolType type
    ) {

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
                    remoteAddress(ctx),
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

    private void sendAlelsAck(
            ChannelHandlerContext ctx,
            String imei,
            String ackType
    ) throws Exception {
        ctx.writeAndFlush(
                Unpooled.wrappedBuffer("01\n".getBytes(StandardCharsets.UTF_8))
        );

        System.out.println("[ACK] " + ackType + " ACK=01 imei=" + imei);

        TcpLogRepository.insert(
                imei,
                remoteAddress(ctx),
                ChannelType.WIFI.name(),
                ProtocolType.ALELS_JSON.name(),
                "ACK_SENT",
                ackType + " ACK sent",
                null,
                3
        );
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

    private void handleAlelsHeartbeatRaw(
            ChannelHandlerContext ctx,
            byte[] packet
    ) throws Exception {
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

        sendAlelsAck(ctx, imei, "ALELS HB");

        RawPacketService.logRawPacket(
                imei,
                ProtocolType.ALELS_JSON,
                ChannelType.WIFI,
                packet,
                remoteAddress(ctx)
        );

        registerNettySession(
                ctx,
                imei,
                ChannelType.WIFI,
                ProtocolType.ALELS_JSON
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
                + " remote=" + remoteAddress(ctx));

        DeviceSessionManager.printSessions();
    }

    private void handleAlelsJson(
            ChannelHandlerContext ctx,
            byte[] packet
    ) throws Exception {
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

        sendAlelsAck(ctx, result.getImei(), ackType);

        RawPacketService.logRawPacket(
                result.getImei(),
                ProtocolType.ALELS_JSON,
                ChannelType.WIFI,
                packet,
                remoteAddress(ctx)
        );

        registerNettySession(
                ctx,
                result.getImei(),
                ChannelType.WIFI,
                ProtocolType.ALELS_JSON
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
            DeviceSessionManager.printSessions();
            return;
        }

        if (isId || isHeartbeat) {
            DeviceSessionManager.printSessions();
            return;
        }

        TelemetryData telemetryData = AlelsJsonTelemetryNormalizer.normalize(packet);

        if (telemetryData != null) {
            telemetryData.print();

            telemetryPublisher.publish(
                    telemetryData,
                    ProtocolType.ALELS_JSON.name(),
                    ChannelType.WIFI.name(),
                    dictionaryCode,
                    "ALELS_JSON"
            );

            printDictionaryIo(telemetryData, dictionaryCode);
        }

        DeviceSessionManager.printSessions();

        lastAlelsCommandName = "ping";
    }

    private void handleTeltonikaImei(
            ChannelHandlerContext ctx,
            byte[] packet
    ) throws Exception {
        TeltonikaImeiParser parser = new TeltonikaImeiParser();
        ParserResult result = parser.parse(packet);

        if (!result.isValid()) {
            ctx.writeAndFlush(
                    Unpooled.wrappedBuffer(new byte[]{0x00})
            );
            return;
        }

        this.boundImei = result.getImei();
        this.sessionImei = result.getImei();
        this.sessionChannel = ChannelType.GSM;

        if (!DeviceReceiveStatusRepository.isReceiveAllowed(result.getImei())) {
            ctx.writeAndFlush(
                    Unpooled.wrappedBuffer(new byte[]{0x00})
            );
            System.out.println("[DEVICE RECEIVE] REJECT imei=" + result.getImei() + " status=SUSPENDED");
            return;
        }

        ensureDeviceProvisioned(
                boundImei,
                ProtocolType.TELTONIKA_IMEI
        );

        System.out.println("[IMEI BIND] socket=" + remoteAddress(ctx)
                + " imei=" + boundImei);

        RawPacketService.logRawPacket(
                boundImei,
                ProtocolType.TELTONIKA_IMEI,
                ChannelType.GSM,
                packet,
                remoteAddress(ctx)
        );

        registerNettySession(
                ctx,
                boundImei,
                ChannelType.GSM,
                ProtocolType.TELTONIKA_IMEI
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

        ctx.writeAndFlush(
                Unpooled.wrappedBuffer(new byte[]{0x01})
        );

        System.out.println("[IMEI] " + boundImei);
        System.out.println("[ACK] TELTONIKA IMEI ACK=01");

        TcpLogRepository.insert(
                boundImei,
                remoteAddress(ctx),
                ChannelType.GSM.name(),
                ProtocolType.TELTONIKA_IMEI.name(),
                "IMEI_RECEIVED",
                "Teltonika IMEI received and accepted",
                packet.length,
                1
        );

        TcpLogRepository.insert(
                boundImei,
                remoteAddress(ctx),
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

    private void handleTeltonikaCodec8(
            ChannelHandlerContext ctx,
            byte[] packet
    ) throws Exception {
        TeltonikaCodec8Parser parser = new TeltonikaCodec8Parser();
        ParserResult result = parser.parse(packet);

        int acceptedRecords = result.isValid() ? result.getRecordCount() : 0;

        if (!DeviceReceiveStatusRepository.isReceiveAllowed(boundImei)) {
            ctx.writeAndFlush(Unpooled.wrappedBuffer(ByteBuffer.allocate(4).putInt(0).array()));
            System.out.println("[DEVICE RECEIVE] REJECT imei=" + boundImei + " status=SUSPENDED");
            return;
        }

        ensureDeviceProvisioned(
                boundImei,
                ProtocolType.TELTONIKA_CODEC8
        );

        String dictionaryCode =
                resolveDictionaryCode(boundImei);

        RawPacketService.logRawPacket(
                boundImei,
                ProtocolType.TELTONIKA_CODEC8,
                ChannelType.GSM,
                packet,
                remoteAddress(ctx)
        );

        if (result.hasTelemetry()) {
            result.getTelemetryList().forEach(t -> {
                t.setImei(boundImei);
                t.print();

                telemetryPublisher.publish(
                        t,
                        ProtocolType.TELTONIKA_CODEC8.name(),
                        ChannelType.GSM.name(),
                        dictionaryCode,
                        "CODEC8"
                );

                printDictionaryIo(t, dictionaryCode);
            });
        }

        if (boundImei != null && !boundImei.isBlank()) {
            registerNettySession(
                    ctx,
                    boundImei,
                    ChannelType.GSM,
                    ProtocolType.TELTONIKA_CODEC8
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

        ctx.writeAndFlush(
                Unpooled.wrappedBuffer(ack)
        );

        System.out.println("[ACK] TELTONIKA CODEC8 ACK=" + acceptedRecords);

        TcpLogRepository.insert(
                boundImei,
                remoteAddress(ctx),
                ChannelType.GSM.name(),
                ProtocolType.TELTONIKA_CODEC8.name(),
                "ACK_SENT",
                "Teltonika Codec8 AVL ACK sent",
                null,
                ack.length
        );
    }

    private void handleTeltonikaCodec8E(
            ChannelHandlerContext ctx,
            byte[] packet
    ) throws Exception {
        TeltonikaCodec8EParser parser = new TeltonikaCodec8EParser();
        ParserResult result = parser.parse(packet);

        int acceptedRecords = result.isValid() ? result.getRecordCount() : 0;

        if (!DeviceReceiveStatusRepository.isReceiveAllowed(boundImei)) {
            ctx.writeAndFlush(Unpooled.wrappedBuffer(ByteBuffer.allocate(4).putInt(0).array()));
            System.out.println("[DEVICE RECEIVE] REJECT imei=" + boundImei + " status=SUSPENDED");
            return;
        }

        ensureDeviceProvisioned(
                boundImei,
                ProtocolType.TELTONIKA_CODEC8E
        );

        String dictionaryCode =
                resolveDictionaryCode(boundImei);

        RawPacketService.logRawPacket(
                boundImei,
                ProtocolType.TELTONIKA_CODEC8E,
                ChannelType.GSM,
                packet,
                remoteAddress(ctx)
        );

        if (result.hasTelemetry()) {
            result.getTelemetryList().forEach(t -> {
                t.setImei(boundImei);
                t.print();

                telemetryPublisher.publish(
                        t,
                        ProtocolType.TELTONIKA_CODEC8E.name(),
                        ChannelType.GSM.name(),
                        dictionaryCode,
                        "CODEC8E"
                );

                printDictionaryIo(t, dictionaryCode);
            });
        }

        if (boundImei != null && !boundImei.isBlank()) {
            registerNettySession(
                    ctx,
                    boundImei,
                    ChannelType.GSM,
                    ProtocolType.TELTONIKA_CODEC8E
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

        ctx.writeAndFlush(
                Unpooled.wrappedBuffer(ack)
        );

        System.out.println("[ACK] TELTONIKA CODEC8E ACK=" + acceptedRecords);

        TcpLogRepository.insert(
                boundImei,
                remoteAddress(ctx),
                ChannelType.GSM.name(),
                ProtocolType.TELTONIKA_CODEC8E.name(),
                "ACK_SENT",
                "Teltonika Codec8E AVL ACK sent",
                null,
                ack.length
        );
    }

    private void handleTeltonikaCodec12Response(
            ChannelHandlerContext ctx,
            byte[] packet
    ) throws Exception {
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
                remoteAddress(ctx)
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
                    remoteAddress(ctx),
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
                    remoteAddress(ctx),
                    ChannelType.GSM.name(),
                    ProtocolType.TELTONIKA_CODEC12_RESPONSE.name(),
                    "COMMAND_RESPONSE_INVALID",
                    "Invalid Teltonika Codec12 response received",
                    packet.length,
                    null
            );
        }
    }

    private void registerNettySession(
            ChannelHandlerContext ctx,
            String imei,
            ChannelType channel,
            ProtocolType protocol
    ) {
        NettyCommandWriter writer =
                new NettyCommandWriter(
                        imei,
                        ctx.channel()
                );

        DeviceSessionRegistry.registerOrUpdate(
                imei,
                channel,
                protocol,
                remoteAddress(ctx),
                writer
        );

        DeviceSessionManager.registerOrUpdate(
                imei,
                channel,
                protocol,
                writer
        );
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

    private String remoteAddress(ChannelHandlerContext ctx) {
        return ctx.channel()
                .remoteAddress()
                .toString();
    }
}
