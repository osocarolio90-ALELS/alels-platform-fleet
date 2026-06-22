package com.alels.gateway.command;

import com.alels.gateway.server.ChannelType;
import com.alels.gateway.netty.DeviceSessionRegistry;
import com.alels.gateway.session.DeviceChannelTracker;

public class CommandProtocolSelector {

    private static final boolean DEBUG_COMMAND_PREVIEW = false;

    public enum CommandRoute {
        TELTONIKA_CODEC12,
        ALELS_JSON_COMMAND,
        DEVICE_OFFLINE
    }

    public static CommandRoute selectRoute(String imei) {
        ChannelType activeNetwork =
                DeviceSessionRegistry.getActiveNetwork(imei);

        if (activeNetwork == ChannelType.GSM) {
            return CommandRoute.TELTONIKA_CODEC12;
        }

        if (activeNetwork == ChannelType.WIFI) {
            return CommandRoute.ALELS_JSON_COMMAND;
        }

        DeviceChannelTracker.ChannelState state = DeviceChannelTracker.getState(imei);

        if (state == null) {
            return CommandRoute.DEVICE_OFFLINE;
        }

        if (state.activeChannel == ChannelType.GSM) {
            return CommandRoute.TELTONIKA_CODEC12;
        }

        if (state.activeChannel == ChannelType.WIFI) {
            return CommandRoute.ALELS_JSON_COMMAND;
        }

        return CommandRoute.DEVICE_OFFLINE;
    }

    public static void printRoute(String imei, String command) {
        if (!DEBUG_COMMAND_PREVIEW) {
            return;
        }

        CommandRoute route = selectRoute(imei);

        System.out.println("[COMMAND SELECTOR] imei=" + imei
                + " command=" + command
                + " route=" + route);

        if (route == CommandRoute.ALELS_JSON_COMMAND) {
            String payload = AlelsJsonCommandBuilder.build(imei, command);
            System.out.println("[COMMAND PREVIEW] ALELS JSON = " + payload);
            return;
        }

        if (route == CommandRoute.TELTONIKA_CODEC12) {
            String hex = TeltonikaCodec12CommandBuilder.buildHex(command);
            System.out.println("[COMMAND PREVIEW] TELTONIKA CODEC12 HEX = " + hex);
            return;
        }

        System.out.println("[COMMAND PREVIEW] FAILED: device offline");
    }
}
