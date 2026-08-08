package com.alels.gateway.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.buffer.PooledByteBufAllocator;
import com.alels.gateway.observability.service.GatewayRuntimeMetrics;

public class NettyTcpServer {

    private final int port;
    private final GatewayRuntimeMetrics metrics;

    public NettyTcpServer(int port, GatewayRuntimeMetrics metrics) {
        this.port = port;
        this.metrics = metrics;
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
                    .option(ChannelOption.SO_BACKLOG, Integer.parseInt(System.getenv().getOrDefault("ALELS_GATEWAY_BACKLOG", "65535")))
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new io.netty.channel.WriteBufferWaterMark(32 * 1024, 128 * 1024));

            ChannelFuture future =
                    bootstrap.bind(port).sync();

            metrics.markAccepting();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                metrics.markDraining();
                future.channel().close().awaitUninterruptibly();
            }, "gateway-drain-hook"));

            System.out.println("[ALELS-GATEWAY] Listening on TCP port " + port);
            System.out.println("[ALELS-GATEWAY] Netty TCP server started");

            future.channel()
                    .closeFuture()
                    .sync();

        } finally {
            metrics.markDraining();
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
