package com.alels.gateway.admission.service;

import com.alels.gateway.observability.service.GatewayRuntimeMetrics;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public final class ConnectionAdmissionHandler extends ChannelInboundHandlerAdapter {
    private final GatewayRuntimeMetrics metrics;
    private boolean admitted;

    public ConnectionAdmissionHandler(GatewayRuntimeMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        admitted = metrics.admitConnection();
        if (!admitted) {
            context.close();
            return;
        }
        super.channelActive(context);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        if (admitted) {
            admitted = false;
            metrics.connectionClosed();
        }
        super.channelInactive(context);
    }
}
