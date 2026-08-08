package com.alels.gateway.netty;

import com.alels.gateway.admission.service.DeviceAdmissionRegistry;
import com.alels.gateway.command.service.CommandResponsePersistenceService;
import com.alels.gateway.detector.ProtocolDetector;
import com.alels.gateway.detector.ProtocolType;
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
import com.alels.gateway.server.ChannelType;
import com.alels.gateway.service.ProtocolRegistryResolver;
import com.alels.gateway.util.HexUtil;
import com.alels.gateway.observability.service.GatewayRuntimeMetrics;
import com.alels.gateway.cell.config.CellRoutingConfig;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import com.alels.gateway.session.ownership.SessionOwnershipConfig;
import com.alels.gateway.session.ownership.SessionOwnershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Network hot path. Database persistence deliberately belongs to ingestion;
 * this handler only parses, checks in-memory admission, publishes, and ACKs.
 */
public final class NettyDeviceChannelHandler extends SimpleChannelInboundHandler<byte[]> {
    private static final Logger log=LoggerFactory.getLogger(NettyDeviceChannelHandler.class);

    private static final TelemetryPublisher PUBLISHER = TelemetryPublisherFactory.create();
    private static final GatewayRuntimeMetrics METRICS = GatewayRuntimeMetrics.instance();
    private static final AlelsJsonParser ALELS_PARSER = new AlelsJsonParser();
    private static final TeltonikaImeiParser IMEI_PARSER = new TeltonikaImeiParser();
    private static final TeltonikaCodec8Parser CODEC8_PARSER = new TeltonikaCodec8Parser();
    private static final TeltonikaCodec8EParser CODEC8E_PARSER = new TeltonikaCodec8EParser();
    private static final TeltonikaCodec12ResponseParser CODEC12_PARSER =
            new TeltonikaCodec12ResponseParser();
    private static final SessionOwnershipService SESSION_OWNERSHIP =
            new SessionOwnershipService(SessionOwnershipConfig.fromEnvironment());

    private final String sessionFencingToken = UUID.randomUUID().toString();
    private String boundImei;
    private String sessionImei;
    private ChannelType sessionChannel;
    private NettyCommandWriter sessionWriter;
    private String lastTeltonikaCommandName;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] packet) throws Exception {
        METRICS.frameReceived(packet.length);
        ProtocolType type = ProtocolDetector.detect(packet);
        if (type != ProtocolType.UNKNOWN && !ProtocolRegistryResolver.isActive(type)) {
            ctx.close();
            return;
        }

        switch (type) {
            case ALELS_JSON -> handleAlelsJson(ctx, packet);
            case TELTONIKA_IMEI -> handleTeltonikaImei(ctx, packet);
            case TELTONIKA_CODEC8 -> handleTeltonikaCodec8(ctx, packet);
            case TELTONIKA_CODEC8E -> handleTeltonikaCodec8E(ctx, packet);
            case TELTONIKA_CODEC12_RESPONSE -> handleTeltonikaCodec12Response(packet);
            default -> {
                if (isAlelsHeartbeat(packet)) {
                    handleAlelsHeartbeat(ctx, packet);
                }
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        DeviceSessionRegistry.markChannelClosed(sessionImei, sessionChannel, sessionWriter);
        SESSION_OWNERSHIP.release(sessionImei, sessionFencingToken)
                .exceptionally(error -> {
                    log.warn("event=session_owner_release_failed imei={} error={}",
                            sessionImei, error.getClass().getSimpleName());
                    return false;
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("event=device_session_error remote={} error={}",remoteAddress(ctx),cause.getClass().getSimpleName());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, event);
    }

    private void handleAlelsJson(ChannelHandlerContext ctx, byte[] packet) throws Exception {
        ParserResult result = ALELS_PARSER.parse(packet);
        if (!result.isValid()) {
            return;
        }
        withOwnership(ctx, result.getImei(), () -> {
            bindSession(ctx, result.getImei(), ChannelType.WIFI, ProtocolType.ALELS_JSON);
            String json = new String(packet, StandardCharsets.UTF_8).trim();
            boolean response = json.contains("\"T\":\"resp\"");
            boolean control = response || json.contains("\"T\":\"id\"") || isAlelsHeartbeat(packet);
            if (response) {
                sendAlelsAck(ctx);
                CommandResponsePersistenceService.persistAlels(packet.clone());
                return;
            }
            if (control) {
                sendAlelsAck(ctx);
                return;
            }
            TelemetryData telemetry;
            try {
                telemetry = AlelsJsonTelemetryNormalizer.normalize(packet);
            } catch (Exception error) {
                log.warn("event=telemetry_normalization_failed imei={} error={}",
                        result.getImei(), error.getClass().getSimpleName());
                return;
            }
            if (telemetry == null) {
                return;
            }
            publish(telemetry, ProtocolType.ALELS_JSON, ChannelType.WIFI, "ALELS_JSON")
                    .whenComplete((ignored, error) -> {
                        if (error == null) sendAlelsAck(ctx);
                        else logPublishFailure(result.getImei(), error);
                    });
        });
    }

    private void handleAlelsHeartbeat(ChannelHandlerContext ctx, byte[] packet) {
        String json = new String(packet, StandardCharsets.UTF_8).trim();
        String imei = extractJsonValue(json, "A");
        if (imei == null) {
            imei = extractJsonValue(json, "imei");
        }
        if (imei == null) {
            imei = sessionImei;
        }
        String heartbeatImei = imei;
        withOwnership(ctx, heartbeatImei, () -> {
            bindSession(ctx, heartbeatImei, ChannelType.WIFI, ProtocolType.ALELS_JSON);
            sendAlelsAck(ctx);
        });
    }

    private void handleTeltonikaImei(ChannelHandlerContext ctx, byte[] packet) {
        ParserResult result = IMEI_PARSER.parse(packet);
        if (!result.isValid()) {
            ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{0x00}));
            return;
        }
        withOwnership(ctx, result.getImei(), () -> {
            boundImei = result.getImei();
            bindSession(ctx, boundImei, ChannelType.GSM, ProtocolType.TELTONIKA_IMEI);
            ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{0x01}));
            lastTeltonikaCommandName = "getinfo";
        });
    }

    private void handleTeltonikaCodec8(ChannelHandlerContext ctx, byte[] packet) {
        handleTeltonikaTelemetry(ctx, CODEC8_PARSER.parse(packet),
                ProtocolType.TELTONIKA_CODEC8, "CODEC8");
    }

    private void handleTeltonikaCodec8E(ChannelHandlerContext ctx, byte[] packet) {
        handleTeltonikaTelemetry(ctx, CODEC8E_PARSER.parse(packet),
                ProtocolType.TELTONIKA_CODEC8E, "CODEC8E");
    }

    private void handleTeltonikaTelemetry(
            ChannelHandlerContext ctx,
            ParserResult result,
            ProtocolType protocol,
            String parserCode
    ) {
        if (!result.isValid()) {
            sendTeltonikaAck(ctx, 0);
            return;
        }
        withOwnership(ctx, boundImei, () -> {
            bindSession(ctx, boundImei, ChannelType.GSM, protocol);
            List<CompletableFuture<Long>> futures = new ArrayList<>();
            for (TelemetryData telemetry : result.getTelemetryList()) {
                telemetry.setImei(boundImei);
                futures.add(publish(telemetry, protocol, ChannelType.GSM, parserCode));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .whenComplete((ignored, error) -> {
                        if (error == null) sendTeltonikaAck(ctx, result.getRecordCount());
                        else {
                            sendTeltonikaAck(ctx, 0);
                            logPublishFailure(boundImei, error);
                        }
                    });
        });
    }

    private void handleTeltonikaCodec12Response(byte[] packet) {
        ParserResult result = CODEC12_PARSER.parse(packet);
        String commandName = lastTeltonikaCommandName == null
                ? "UNKNOWN_CODEC12_COMMAND" : lastTeltonikaCommandName;
        CommandResponsePersistenceService.persistTeltonika(
                boundImei,
                commandName,
                result.isValid(),
                HexUtil.toHex(packet)
        );
    }

    private CompletableFuture<Long> publish(
            TelemetryData telemetry,
            ProtocolType protocol,
            ChannelType channel,
            String parserCode
    ) {
        if (!METRICS.beginPublish()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Gateway publish capacity is saturated")
            );
        }
        CompletableFuture<Long> result = PUBLISHER.publish(
                telemetry,
                protocol.name(),
                channel.name(),
                DeviceAdmissionRegistry.dictionaryCode(telemetry.getImei()),
                parserCode
        ).toCompletableFuture();
        result.whenComplete((ignored, error) -> METRICS.publishCompleted(error == null));
        return result;
    }

    private boolean admit(String imei) {
        if (!CellRoutingConfig.current().owns(imei)) {
            METRICS.wrongCellRejected();
            return false;
        }
        return DeviceAdmissionRegistry.isReceiveAllowed(imei);
    }

    private void withOwnership(ChannelHandlerContext ctx, String imei, Runnable action) {
        if (!admit(imei)) {
            ctx.close();
            return;
        }
        ctx.channel().config().setAutoRead(false);
        SESSION_OWNERSHIP.acquireOrRenew(imei, sessionFencingToken)
                .whenComplete((owned, error) -> ctx.executor().execute(() -> {
                    try {
                        if (error != null || !Boolean.TRUE.equals(owned)) {
                            METRICS.sessionOwnershipRejected();
                            log.warn("event=session_owner_rejected imei={} error={}", imei,
                                    error == null ? "owned_by_other_gateway"
                                            : error.getClass().getSimpleName());
                            ctx.close();
                            return;
                        }
                        action.run();
                    } finally {
                        if (ctx.channel().isActive()) {
                            ctx.channel().config().setAutoRead(true);
                            ctx.read();
                        }
                    }
                }));
    }

    public static void shutdownSessionOwnership() {
        SESSION_OWNERSHIP.close();
    }

    private void bindSession(
            ChannelHandlerContext ctx,
            String imei,
            ChannelType channel,
            ProtocolType protocol
    ) {
        if (sessionWriter == null || sessionImei == null || !sessionImei.equals(imei)) {
            sessionWriter = new NettyCommandWriter(imei, ctx.channel());
        }
        sessionImei = imei;
        sessionChannel = channel;
        DeviceSessionRegistry.registerOrUpdate(
                imei, channel, protocol, remoteAddress(ctx), sessionWriter
        );
    }

    private void sendAlelsAck(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer("01\n".getBytes(StandardCharsets.UTF_8)));
    }

    private void sendTeltonikaAck(ChannelHandlerContext ctx, int recordCount) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(ByteBuffer.allocate(4).putInt(recordCount).array()));
    }

    private boolean isAlelsHeartbeat(byte[] packet) {
        String json = new String(packet, StandardCharsets.UTF_8).trim();
        return json.startsWith("{") && (json.contains("\"T\":\"hb\"")
                || json.contains("\"T\":\"heart\"")
                || json.contains("\"T\":\"ping\""));
    }

    private String extractJsonValue(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end).trim();
    }

    private void logPublishFailure(String imei, Throwable error) {
        log.error("event=telemetry_publish_failed imei={} error={}",imei,error.getClass().getSimpleName());
    }

    private String remoteAddress(ChannelHandlerContext ctx) {
        return String.valueOf(ctx.channel().remoteAddress());
    }
}
