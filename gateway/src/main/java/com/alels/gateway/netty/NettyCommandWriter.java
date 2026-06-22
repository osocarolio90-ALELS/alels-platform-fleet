package com.alels.gateway.netty;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

public class NettyCommandWriter {

    private final String imei;
    private final Channel channel;

    public NettyCommandWriter(
            String imei,
            Channel channel
    ) {
        this.imei = imei;
        this.channel = channel;
    }

    public boolean isWritable() {
        return channel != null
                && channel.isActive()
                && channel.isWritable();
    }

    public boolean sendTextLine(String text) {
        try {
            if (!isWritable()) {
                System.out.println("[SESSION SEND] failed: socket not alive imei=" + imei);
                return false;
            }

            byte[] payload =
                    (text + "\n").getBytes(StandardCharsets.UTF_8);

            channel.writeAndFlush(
                    Unpooled.wrappedBuffer(payload)
            );

            System.out.println("[SESSION SEND] imei=" + imei + " text=" + text);
            return true;

        } catch (Exception e) {
            System.err.println("[SESSION SEND ERROR] imei=" + imei + " " + e.getMessage());
            return false;
        }
    }

    public boolean sendBytes(byte[] data) {
        try {
            if (!isWritable()) {
                System.out.println("[SESSION SEND] failed: socket not alive imei=" + imei);
                return false;
            }

            channel.writeAndFlush(
                    Unpooled.wrappedBuffer(data)
            );

            System.out.println("[SESSION SEND] imei=" + imei + " bytes=" + data.length);
            return true;

        } catch (Exception e) {
            System.err.println("[SESSION SEND ERROR] imei=" + imei + " " + e.getMessage());
            return false;
        }
    }
}
