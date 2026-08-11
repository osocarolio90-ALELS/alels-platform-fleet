package com.alels.gateway.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public final class NettyUdpServer implements AutoCloseable {
    private final int port;
    private EventLoopGroup group;
    private Channel channel;

    public NettyUdpServer(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        group = new NioEventLoopGroup();
        try {
            channel = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, false)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .handler(new NettyUdpDeviceHandler())
                    .bind(port)
                    .sync()
                    .channel();
            System.out.println("[ALELS-GATEWAY] Listening for Teltonika UDP on port " + port);
        } catch (InterruptedException | RuntimeException error) {
            close();
            throw error;
        }
    }

    @Override
    public void close() {
        if (channel != null) channel.close().awaitUninterruptibly();
        if (group != null) group.shutdownGracefully().awaitUninterruptibly();
    }
}
