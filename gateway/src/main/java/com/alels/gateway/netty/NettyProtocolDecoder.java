package com.alels.gateway.netty;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class NettyProtocolDecoder extends ByteToMessageDecoder {

    private static final int MAX_FRAME_BYTES = Integer.parseInt(
            System.getenv().getOrDefault("ALELS_GATEWAY_MAX_FRAME_BYTES", "1048576")
    );

    @Override
    protected void decode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            List<Object> out
    ) {
        if (!in.isReadable()) {
            return;
        }

        while (in.isReadable() && isJsonDelimiter(in.getByte(in.readerIndex()))) {
            in.skipBytes(1);
        }
        if (!in.isReadable()) return;

        int reader = in.readerIndex();
        int readable = in.readableBytes();
        int frameLength;

        if (in.getByte(reader) == '{') {
            frameLength = jsonFrameLength(in, reader, readable);
            if (frameLength == 0) return;
        } else {
            if (readable < 2) return;
            int declaredLength = in.getUnsignedShort(reader);
            if (declaredLength > 0 && declaredLength <= 64) {
                frameLength = declaredLength + 2;
                if (readable < frameLength) return;
            } else if (declaredLength == 0) {
                if (readable < 8) return;
                if (in.getInt(reader) != 0) {
                    throw new IllegalArgumentException("Invalid AVL preamble");
                }
                long dataLength = in.getUnsignedInt(reader + 4);
                frameLength = checkedFrameLength(dataLength + 12L);
                if (readable < frameLength) return;
            } else {
                throw new IllegalArgumentException("Invalid handshake frame length: " + declaredLength);
            }
        }

        byte[] packet = new byte[frameLength];
        in.readBytes(packet);
        out.add(packet);
    }

    private int checkedFrameLength(long length) {
        if (length < 12 || length > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("Frame exceeds configured bounds: " + length);
        }
        return (int) length;
    }

    private int jsonFrameLength(ByteBuf in, int start, int readable) {
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i < readable; i++) {
            byte value = in.getByte(start + i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
            } else if (value == '"') {
                quoted = true;
            } else if (value == '{') {
                depth++;
            } else if (value == '}' && --depth == 0) {
                return i + 1;
            }
            if (i + 1 > MAX_FRAME_BYTES) {
                throw new IllegalArgumentException("JSON frame exceeds configured bounds");
            }
        }
        return 0;
    }

    private boolean isJsonDelimiter(byte value) {
        return value == '\n' || value == '\r' || value == ' ' || value == '\t';
    }
}
