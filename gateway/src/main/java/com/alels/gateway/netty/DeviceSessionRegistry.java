package com.alels.gateway.netty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.server.ChannelType;

public class DeviceSessionRegistry {

    public static final long DEFAULT_PRESENCE_TIMEOUT_MS =
            7L * 60L * 1000L;

    private static final Map<String, DeviceSessionState> sessions =
            new ConcurrentHashMap<>();

    private DeviceSessionRegistry() {
    }

    public static void registerOrUpdate(
            String imei,
            ChannelType channel,
            ProtocolType protocol,
            String remoteAddress,
            NettyCommandWriter writer
    ) {
        if (imei == null || imei.isBlank() || channel == null) {
            return;
        }

        sessions.compute(imei, (key, existing) -> {
            DeviceSessionState state =
                    existing != null
                            ? existing
                            : new DeviceSessionState(imei);

            state.update(
                    channel,
                    protocol,
                    remoteAddress,
                    writer,
                    System.currentTimeMillis()
            );

            return state;
        });
    }

    public static void markChannelClosed(
            String imei,
            ChannelType channel,
            NettyCommandWriter writer
    ) {
        if (imei == null || imei.isBlank() || channel == null) {
            return;
        }

        DeviceSessionState state =
                sessions.get(imei);

        if (state == null) {
            return;
        }

        state.markClosed(channel, writer);

        if (state.getActiveNetwork() == ChannelType.UNKNOWN) {
            sessions.remove(imei, state);
        }

    }

    public static DeviceSessionState getSession(String imei) {
        DeviceSessionState state =
                sessions.get(imei);

        if (state == null) {
            return null;
        }

        state.refreshPresence(
                System.currentTimeMillis()
        );

        return state;
    }

    public static ChannelType getActiveNetwork(String imei) {
        DeviceSessionState state =
                getSession(imei);

        if (state == null) {
            return ChannelType.UNKNOWN;
        }

        return state.getActiveNetwork();
    }

    public static ProtocolType getActiveProtocol(String imei) {
        DeviceSessionState state =
                getSession(imei);

        if (state == null) {
            return ProtocolType.UNKNOWN;
        }

        return state.getActiveProtocol();
    }

    public static NettyCommandWriter getActiveWriter(String imei) {
        DeviceSessionState state =
                getSession(imei);

        if (state == null) {
            return null;
        }

        return state.getActiveWriter();
    }

    public static class DeviceSessionState {
        private final String imei;

        private final ChannelSession gsm =
                new ChannelSession(ChannelType.GSM);

        private final ChannelSession wifi =
                new ChannelSession(ChannelType.WIFI);

        private ChannelType activeNetwork =
                ChannelType.UNKNOWN;

        private ProtocolType activeProtocol =
                ProtocolType.UNKNOWN;

        private long lastSeenMs;

        private String remoteAddress;

        private DeviceSessionState(String imei) {
            this.imei = imei;
        }

        private synchronized void update(
                ChannelType channel,
                ProtocolType protocol,
                String remoteAddress,
                NettyCommandWriter writer,
                long now
        ) {
            ChannelSession target =
                    channel == ChannelType.GSM
                            ? gsm
                            : wifi;

            target.protocol = protocol;
            target.lastSeenMs = now;
            target.remoteAddress = remoteAddress;
            target.writer = writer;
            target.connected = true;
            target.online = true;

            this.lastSeenMs = now;
            this.remoteAddress = remoteAddress;

            refreshPresence(now);
            selectActiveNetwork();
        }

        private synchronized void markClosed(ChannelType channel, NettyCommandWriter writer) {
            ChannelSession target =
                    channel == ChannelType.GSM
                            ? gsm
                            : wifi;

            if (target.writer != writer) {
                return;
            }

            target.connected = false;
            target.writer = null;

            selectActiveNetwork();
        }

        private synchronized void refreshPresence(long now) {
            gsm.online =
                    gsm.lastSeenMs > 0
                            && now - gsm.lastSeenMs <= DEFAULT_PRESENCE_TIMEOUT_MS;

            wifi.online =
                    wifi.lastSeenMs > 0
                            && now - wifi.lastSeenMs <= DEFAULT_PRESENCE_TIMEOUT_MS;

            selectActiveNetwork();
        }

        private void selectActiveNetwork() {
            ChannelSession preferred =
                    newerOnline(gsm, wifi);

            if (preferred == null) {
                activeNetwork = ChannelType.UNKNOWN;
                activeProtocol = ProtocolType.UNKNOWN;
                return;
            }

            activeNetwork = preferred.channel;
            activeProtocol =
                    preferred.protocol != null
                            ? preferred.protocol
                            : ProtocolType.UNKNOWN;
        }

        private ChannelSession newerOnline(
                ChannelSession first,
                ChannelSession second
        ) {
            boolean firstUsable =
                    first.online
                            && first.connected
                            && first.writer != null
                            && first.writer.isWritable();

            boolean secondUsable =
                    second.online
                            && second.connected
                            && second.writer != null
                            && second.writer.isWritable();

            if (firstUsable && secondUsable) {
                return first.lastSeenMs >= second.lastSeenMs
                        ? first
                        : second;
            }

            if (firstUsable) {
                return first;
            }

            if (secondUsable) {
                return second;
            }

            return null;
        }

        public String getImei() {
            return imei;
        }

        public ChannelType getChannel() {
            return activeNetwork;
        }

        public ProtocolType getProtocol() {
            return activeProtocol;
        }

        public ChannelType getActiveNetwork() {
            return activeNetwork;
        }

        public ProtocolType getActiveProtocol() {
            return activeProtocol;
        }

        public long getLastSeenMs() {
            return lastSeenMs;
        }

        public String getRemoteAddress() {
            return remoteAddress;
        }

        public NettyCommandWriter getActiveWriter() {
            if (activeNetwork == ChannelType.GSM) {
                return gsm.writer;
            }

            if (activeNetwork == ChannelType.WIFI) {
                return wifi.writer;
            }

            return null;
        }
    }

    private static class ChannelSession {
        private final ChannelType channel;
        private ProtocolType protocol =
                ProtocolType.UNKNOWN;
        private long lastSeenMs;
        private String remoteAddress;
        private NettyCommandWriter writer;
        private boolean connected;
        private boolean online;

        private ChannelSession(ChannelType channel) {
            this.channel = channel;
        }
    }
}
