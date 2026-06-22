package com.alels.gateway.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.server.ChannelType;

public class DeviceChannelTracker {

    private static final long ONLINE_TIMEOUT_MS = 10_000;
    private static final Map<String, ChannelState> channelStates = new ConcurrentHashMap<>();

    public static void updateChannel(String imei, ChannelType channel, ProtocolType protocol) {
        if (imei == null || imei.isBlank()) {
            System.out.println("[CHANNEL] skipped: empty imei");
            return;
        }

        ChannelState state = channelStates.computeIfAbsent(imei, ChannelState::new);
        long now = System.currentTimeMillis();

        if (channel == ChannelType.GSM) {
            state.gsmOnline = true;
            state.lastGsmSeen = now;
            state.lastGsmProtocol = protocol;
        } else if (channel == ChannelType.WIFI) {
            state.wifiOnline = true;
            state.lastWifiSeen = now;
            state.lastWifiProtocol = protocol;
        }

        refreshOnlineStatus(state, now);
        selectActiveChannel(state);

        printState(state);
    }

    public static ChannelState getState(String imei) {
        ChannelState state = channelStates.get(imei);
        if (state == null) return null;

        long now = System.currentTimeMillis();
        refreshOnlineStatus(state, now);
        selectActiveChannel(state);

        return state;
    }

    private static void refreshOnlineStatus(ChannelState state, long now) {
        state.gsmOnline = state.lastGsmSeen > 0 && (now - state.lastGsmSeen <= ONLINE_TIMEOUT_MS);
        state.wifiOnline = state.lastWifiSeen > 0 && (now - state.lastWifiSeen <= ONLINE_TIMEOUT_MS);
    }

    private static void selectActiveChannel(ChannelState state) {
        if (state.gsmOnline) {
            state.activeChannel = ChannelType.GSM;
            state.activeProtocol = state.lastGsmProtocol;
        } else if (state.wifiOnline) {
            state.activeChannel = ChannelType.WIFI;
            state.activeProtocol = state.lastWifiProtocol;
        } else {
            state.activeChannel = ChannelType.UNKNOWN;
            state.activeProtocol = ProtocolType.UNKNOWN;
        }
    }

    public static void printState(ChannelState state) {
        System.out.println("[CHANNEL] imei=" + state.imei
                + " gsm=" + state.gsmOnline
                + " wifi=" + state.wifiOnline
                + " active=" + state.activeChannel
                + " protocol=" + state.activeProtocol);
    }

    public static class ChannelState {
        public final String imei;

        public boolean gsmOnline = false;
        public boolean wifiOnline = false;

        public long lastGsmSeen = 0;
        public long lastWifiSeen = 0;

        public ProtocolType lastGsmProtocol = ProtocolType.UNKNOWN;
        public ProtocolType lastWifiProtocol = ProtocolType.UNKNOWN;

        public ChannelType activeChannel = ChannelType.UNKNOWN;
        public ProtocolType activeProtocol = ProtocolType.UNKNOWN;

        public ChannelState(String imei) {
            this.imei = imei;
        }
    }
}