package com.alels.gateway.session;

import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.netty.NettyCommandWriter;
import com.alels.gateway.server.ChannelType;

public class DeviceSession {
    private final String imei;

    private ChannelType channelType;
    private ProtocolType protocolType;
    private Socket socket;
    private NettyCommandWriter nettyCommandWriter;
    private long lastSeenMillis;

    public DeviceSession(
            String imei,
            ChannelType channelType,
            ProtocolType protocolType,
            Socket socket
    ) {
        this.imei = imei;
        this.channelType = channelType;
        this.protocolType = protocolType;
        this.socket = socket;
        this.lastSeenMillis = System.currentTimeMillis();
    }

    public DeviceSession(
            String imei,
            ChannelType channelType,
            ProtocolType protocolType,
            NettyCommandWriter nettyCommandWriter
    ) {
        this.imei = imei;
        this.channelType = channelType;
        this.protocolType = protocolType;
        this.nettyCommandWriter = nettyCommandWriter;
        this.lastSeenMillis = System.currentTimeMillis();
    }

    public String getImei() {
        return imei;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public Socket getSocket() {
        return socket;
    }

    public long getLastSeenMillis() {
        return lastSeenMillis;
    }

    public void update(
            ChannelType channelType,
            ProtocolType protocolType,
            Socket socket
    ) {
        this.channelType = channelType;
        this.protocolType = protocolType;
        this.socket = socket;
        this.nettyCommandWriter = null;
        this.lastSeenMillis = System.currentTimeMillis();
    }

    public void update(
            ChannelType channelType,
            ProtocolType protocolType,
            NettyCommandWriter nettyCommandWriter
    ) {
        this.channelType = channelType;
        this.protocolType = protocolType;
        this.socket = null;
        this.nettyCommandWriter = nettyCommandWriter;
        this.lastSeenMillis = System.currentTimeMillis();
    }

    public boolean isOnline(long timeoutMillis) {
        return System.currentTimeMillis() - lastSeenMillis <= timeoutMillis;
    }

    public boolean isSocketAlive() {
        if (nettyCommandWriter != null) {
            return nettyCommandWriter.isWritable();
        }

        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    public boolean sendTextLine(String text) {
        try {
            if (nettyCommandWriter != null) {
                return nettyCommandWriter.sendTextLine(text);
            }

            if (!isSocketAlive()) {
                System.out.println("[SESSION SEND] failed: socket not alive imei=" + imei);
                return false;
            }

            socket.getOutputStream().write((text + "\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();

            System.out.println("[SESSION SEND] imei=" + imei + " text=" + text);
            return true;

        } catch (Exception e) {
            System.err.println("[SESSION SEND ERROR] imei=" + imei + " " + e.getMessage());
            return false;
        }
    }

    public boolean sendBytes(byte[] data) {
        try {
            if (nettyCommandWriter != null) {
                return nettyCommandWriter.sendBytes(data);
            }

            if (!isSocketAlive()) {
                System.out.println("[SESSION SEND] failed: socket not alive imei=" + imei);
                return false;
            }

            socket.getOutputStream().write(data);
            socket.getOutputStream().flush();

            System.out.println("[SESSION SEND] imei=" + imei + " bytes=" + data.length);
            return true;

        } catch (Exception e) {
            System.err.println("[SESSION SEND ERROR] imei=" + imei + " " + e.getMessage());
            return false;
        }
    }
}
