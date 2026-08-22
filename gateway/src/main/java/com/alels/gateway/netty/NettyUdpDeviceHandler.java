package com.alels.gateway.netty;

import com.alels.gateway.admission.service.DeviceAdmissionRegistry;
import com.alels.gateway.cell.config.CellRoutingConfig;
import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.observability.service.GatewayRuntimeMetrics;
import com.alels.gateway.parser.ParserResult;
import com.alels.gateway.parser.TeltonikaCodec8EParser;
import com.alels.gateway.parser.TeltonikaCodec8Parser;
import com.alels.gateway.parser.TeltonikaUdpDatagram;
import com.alels.gateway.parser.TeltonikaUdpDatagramParser;
import com.alels.gateway.publisher.TelemetryPublisherFactory;
import com.alels.gateway.server.ChannelType;
import com.alels.gateway.service.ProtocolRegistryResolver;
import com.alels.gateway.service.TelemetryBatchPublisher;
import com.alels.gateway.service.PacketAuditService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class NettyUdpDeviceHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private static final Logger log = LoggerFactory.getLogger(NettyUdpDeviceHandler.class);
    private static final GatewayRuntimeMetrics METRICS = GatewayRuntimeMetrics.instance();
    private static final TeltonikaUdpDatagramParser UDP = new TeltonikaUdpDatagramParser();
    private static final TeltonikaCodec8Parser CODEC8 = new TeltonikaCodec8Parser();
    private static final TeltonikaCodec8EParser CODEC8E = new TeltonikaCodec8EParser();
    private static final TelemetryBatchPublisher BATCH = new TelemetryBatchPublisher(
            TelemetryPublisherFactory.create(), METRICS
    );

    @Override
    protected void channelRead0(ChannelHandlerContext context, DatagramPacket packet) {
        ByteBuf content = packet.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.getBytes(content.readerIndex(), bytes);
        METRICS.frameReceived(bytes.length);

        UDP.parse(bytes).ifPresentOrElse(
                datagram -> process(context, packet, datagram, bytes),
                () -> log.warn("event=teltonika_udp_invalid remote={} bytes={}", packet.sender(), bytes.length)
        );
    }

    private void process(ChannelHandlerContext context, DatagramPacket packet, TeltonikaUdpDatagram datagram,
                         byte[] rawDatagram) {
        ProtocolType protocol = datagram.codecId() == 0x08
                ? ProtocolType.TELTONIKA_CODEC8 : ProtocolType.TELTONIKA_CODEC8E;
        if (!CellRoutingConfig.current().owns(datagram.imei())
                || !ProtocolRegistryResolver.isActive(protocol)
                || !DeviceAdmissionRegistry.isReceiveAllowed(datagram.imei())) {
            return;
        }

        ParserResult parsed = protocol == ProtocolType.TELTONIKA_CODEC8
                ? CODEC8.parse(datagram.tcpCompatiblePacket())
                : CODEC8E.parse(datagram.tcpCompatiblePacket());
        if (!parsed.isValid()) {
            respond(context, packet, UDP.acknowledgement(datagram, 0), datagram.imei(), protocol);
            return;
        }

        PacketAuditService.persistReceived(
                datagram.imei(), protocol, "UDP", rawDatagram, String.valueOf(packet.sender())
        ).whenComplete((rawPacketId, auditError) -> context.executor().execute(() -> {
            if (auditError != null || rawPacketId == null) {
                respond(context, packet, UDP.acknowledgement(datagram, 0), datagram.imei(), protocol);
                log.error("event=teltonika_udp_audit_failed imei={} error={}", datagram.imei(),
                        auditError == null ? "missing_raw_packet_id" : auditError.getClass().getSimpleName());
                return;
            }
            BATCH.publish(
                    rawPacketId, parsed, datagram.imei(), protocol, ChannelType.GSM,
                    DeviceAdmissionRegistry.dictionaryCode(datagram.imei()),
                    protocol == ProtocolType.TELTONIKA_CODEC8 ? "CODEC8" : "CODEC8E"
            ).whenComplete((accepted, error) -> context.executor().execute(() -> {
                if (error == null) respond(context, packet, UDP.acknowledgement(datagram, accepted), datagram.imei(), protocol);
                else {
                    respond(context, packet, UDP.acknowledgement(datagram, 0), datagram.imei(), protocol);
                    log.error("event=teltonika_udp_publish_failed imei={} error={}",
                            datagram.imei(), error.getClass().getSimpleName());
                }
            }));
        }));
    }

    private void respond(ChannelHandlerContext context, DatagramPacket request, byte[] response,
                         String imei, ProtocolType protocol) {
        context.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(response), request.sender()))
                .addListener(result -> {
                    if (!result.isSuccess()) return;
                    PacketAuditService.persistSent(imei, protocol, "UDP", response, String.valueOf(request.sender()))
                            .whenComplete((ignored, error) -> {
                                if (error != null) log.error("event=udp_packet_tx_audit_failed imei={} protocol={} error={}",
                                        imei, protocol, error.getClass().getSimpleName());
                            });
                });
    }
}
