package com.alels.gateway.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NettyTcpServer {

    private final int port;

    public NettyTcpServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        EventLoopGroup bossGroup =
                new NioEventLoopGroup(1);

        EventLoopGroup workerGroup =
                new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap =
                    new ServerBootstrap();

            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new NettyServerInitializer())
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future =
                    bootstrap.bind(port).sync();

            System.out.println("[ALELS-GATEWAY] Listening on TCP port " + port);
            System.out.println("[ALELS-GATEWAY] Netty TCP server started");

            future.channel()
                    .closeFuture()
                    .sync();

        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
