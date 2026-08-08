package com.alels.gateway.netty;

import com.alels.gateway.admission.service.ConnectionAdmissionHandler;
import com.alels.gateway.observability.service.GatewayRuntimeMetrics;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final int IDLE_TIMEOUT_SECONDS = Integer.parseInt(
            System.getenv().getOrDefault("ALELS_GATEWAY_IDLE_TIMEOUT_SECONDS", "180")
    );
    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new ConnectionAdmissionHandler(GatewayRuntimeMetrics.instance()))
                .addLast(new IdleStateHandler(IDLE_TIMEOUT_SECONDS, 0, 0))
                .addLast(new NettyProtocolDecoder())
                .addLast(new NettyDeviceChannelHandler());
    }
}
