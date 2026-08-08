package com.alels.gateway.session;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.netty.NettyCommandWriter;
import com.alels.gateway.server.ChannelType;

public class DeviceSessionManager {
    private static final Map<String, DeviceSession> sessions = new ConcurrentHashMap<>();

    public static void registerOrUpdate(
            String imei,
            ChannelType channelType,
            ProtocolType protocolType,
            Socket socket
    ) {
        if (imei == null || imei.isBlank()) {
            System.out.println("[SESSION] skipped: empty imei");
            return;
        }

        sessions.compute(imei, (key, existing) -> {
            if (existing == null) {
                System.out.println("[SESSION] NEW imei=" + imei
                        + " channel=" + channelType
                        + " protocol=" + protocolType);
                return new DeviceSession(imei, channelType, protocolType, socket);
            }

            existing.update(channelType, protocolType, socket);

            System.out.println("[SESSION] UPDATE imei=" + imei
                    + " channel=" + channelType
                    + " protocol=" + protocolType);

            return existing;
        });
    }

    public static void registerOrUpdate(
            String imei,
            ChannelType channelType,
            ProtocolType protocolType,
            NettyCommandWriter nettyCommandWriter
    ) {
        if (imei == null || imei.isBlank()) {
            System.out.println("[SESSION] skipped: empty imei");
            return;
        }

        sessions.compute(imei, (key, existing) -> {
            if (existing == null) {
                System.out.println("[SESSION] NEW imei=" + imei
                        + " channel=" + channelType
                        + " protocol=" + protocolType);
                return new DeviceSession(imei, channelType, protocolType, nettyCommandWriter);
            }

            existing.update(channelType, protocolType, nettyCommandWriter);

            System.out.println("[SESSION] UPDATE imei=" + imei
                    + " channel=" + channelType
                    + " protocol=" + protocolType);

            return existing;
        });
    }

    public static DeviceSession getSession(String imei) {
        return sessions.get(imei);
    }

    public static void printSessions() {
        System.out.println("[SESSION] activeCount=" + sessions.size());
    }

    public static void remove(String imei) {
        if (imei != null) sessions.remove(imei);
    }
}
