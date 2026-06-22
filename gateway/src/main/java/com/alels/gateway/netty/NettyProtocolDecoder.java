package com.alels.gateway.netty;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class NettyProtocolDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            List<Object> out
    ) {
        if (!in.isReadable()) {
            return;
        }

        byte[] packet =
                new byte[in.readableBytes()];

        in.readBytes(packet);

        out.add(packet);
    }
}
