package com.alels.gateway.command;

import com.alels.gateway.repository.CommandRepository;
import com.alels.gateway.repository.TcpLogRepository;
import com.alels.gateway.netty.DeviceSessionRegistry;
import com.alels.gateway.netty.NettyCommandWriter;
import com.alels.gateway.session.DeviceSession;
import com.alels.gateway.session.DeviceSessionManager;

public class CommandDispatcher {

    public static boolean hasActiveChannel(String imei) {
        DeviceSession session =
                DeviceSessionManager.getSession(imei);

        NettyCommandWriter nettyWriter =
                DeviceSessionRegistry.getActiveWriter(imei);

        return session != null || nettyWriter != null;
    }

    public static boolean sendDummyCommand(String imei, String command) {
        return sendCommand(imei, command);
    }

    /*
     * Used by manual/internal gateway command flow.
     * This method INSERTS a new command row into database.
     */
    public static boolean sendCommand(String imei, String command) {
        CommandProtocolSelector.CommandRoute route =
                CommandProtocolSelector.selectRoute(imei);

        DeviceSession session =
                DeviceSessionManager.getSession(imei);

        NettyCommandWriter nettyWriter =
                DeviceSessionRegistry.getActiveWriter(imei);

        if (session == null && nettyWriter == null) {
            System.out.println(
                    "[COMMAND SEND] failed: session not found imei=" + imei
            );

            CommandRepository.insertCommand(
                    imei,
                    command,
                    route.name(),
                    null,
                    CommandStatus.FAILED.name()
            );

            TcpLogRepository.insert(
                    imei,
                    null,
                    null,
                    null,
                    "COMMAND_SEND_FAILED",
                    "Session not found for command=" + command,
                    null,
                    null
            );

            return false;
        }

        if (route == CommandProtocolSelector.CommandRoute.ALELS_JSON_COMMAND) {
            String payload =
                    AlelsJsonCommandBuilder.build(imei, command);

            boolean sent =
                    nettyWriter != null
                            ? nettyWriter.sendTextLine(payload)
                            : session.sendTextLine(payload);

            CommandRepository.insertCommand(
                    imei,
                    command,
                    route.name(),
                    payload,
                    sent
                            ? CommandStatus.SENT.name()
                            : CommandStatus.FAILED.name()
            );

            TcpLogRepository.insert(
                    imei,
                    null,
                    "WIFI",
                    "ALELS_JSON",
                    sent ? "COMMAND_SENT" : "COMMAND_SEND_FAILED",
                    "ALELS JSON command sent: " + command,
                    null,
                    payload.getBytes().length
            );

            System.out.println(
                    "[COMMAND SEND] route=ALELS_JSON_COMMAND sent="
                            + sent
                            + " payload="
                            + payload
            );

            return sent;
        }

        if (route == CommandProtocolSelector.CommandRoute.TELTONIKA_CODEC12) {
            byte[] payload =
                    TeltonikaCodec12CommandBuilder.build(command);

            String hex =
                    TeltonikaCodec12CommandBuilder.buildHex(command);

            boolean sent =
                    nettyWriter != null
                            ? nettyWriter.sendBytes(payload)
                            : session.sendBytes(payload);

            CommandRepository.insertCommand(
                    imei,
                    command,
                    route.name(),
                    hex,
                    sent
                            ? CommandStatus.SENT.name()
                            : CommandStatus.FAILED.name()
            );

            TcpLogRepository.insert(
                    imei,
                    null,
                    "GSM",
                    "TELTONIKA_CODEC12",
                    sent ? "COMMAND_SENT" : "COMMAND_SEND_FAILED",
                    "Teltonika Codec12 command sent: "
                            + command
                            + " hex="
                            + hex,
                    null,
                    payload.length
            );

            System.out.println(
                    "[COMMAND BUILD] TELTONIKA CODEC12 HEX = " + hex
            );

            System.out.println(
                    "[COMMAND SEND] route=TELTONIKA_CODEC12 sent="
                            + sent
                            + " command="
                            + command
            );

            return sent;
        }

        System.out.println(
                "[COMMAND SEND] failed: device offline imei=" + imei
        );

        CommandRepository.insertCommand(
                imei,
                command,
                route.name(),
                null,
                CommandStatus.FAILED.name()
        );

        TcpLogRepository.insert(
                imei,
                null,
                null,
                null,
                "COMMAND_SEND_FAILED",
                "Device offline for command=" + command,
                null,
                null
        );

        return false;
    }

    /*
     * Used by PendingCommandPoller.
     * This method DOES NOT insert a new command row.
     * It only sends the existing queued command and returns true/false.
     */
    public static boolean sendQueuedCommand(
            String imei,
            String command,
            String routeName
    ) {
        CommandProtocolSelector.CommandRoute activeRoute =
                CommandProtocolSelector.selectRoute(imei);

        String effectiveRouteName =
                activeRoute != CommandProtocolSelector.CommandRoute.DEVICE_OFFLINE
                        ? activeRoute.name()
                        : routeName;

        DeviceSession session =
                DeviceSessionManager.getSession(imei);

        NettyCommandWriter nettyWriter =
                DeviceSessionRegistry.getActiveWriter(imei);

        if (session == null && nettyWriter == null) {
            System.out.println(
                    "[QUEUED COMMAND SEND] failed: session not found imei=" + imei
            );

            TcpLogRepository.insert(
                    imei,
                    null,
                    null,
                    null,
                    "QUEUED_COMMAND_SEND_FAILED",
                    "Session not found for queued command=" + command,
                    null,
                    null
            );

            return false;
        }

        if ("ALELS_JSON_COMMAND".equalsIgnoreCase(effectiveRouteName)) {
            String payload =
                    AlelsJsonCommandBuilder.build(imei, command);

            boolean sent =
                    nettyWriter != null
                            ? nettyWriter.sendTextLine(payload)
                            : session.sendTextLine(payload);

            TcpLogRepository.insert(
                    imei,
                    null,
                    "WIFI",
                    "ALELS_JSON",
                    sent ? "QUEUED_COMMAND_SENT" : "QUEUED_COMMAND_SEND_FAILED",
                    "Queued ALELS JSON command sent: " + command,
                    null,
                    payload.getBytes().length
            );

            System.out.println(
                    "[QUEUED COMMAND SEND] route=ALELS_JSON_COMMAND sent="
                            + sent
                            + " payload="
                            + payload
            );

            return sent;
        }

        if ("TELTONIKA_CODEC12".equalsIgnoreCase(effectiveRouteName)) {
            byte[] payload =
                    TeltonikaCodec12CommandBuilder.build(command);

            String hex =
                    TeltonikaCodec12CommandBuilder.buildHex(command);

            boolean sent =
                    nettyWriter != null
                            ? nettyWriter.sendBytes(payload)
                            : session.sendBytes(payload);

            TcpLogRepository.insert(
                    imei,
                    null,
                    "GSM",
                    "TELTONIKA_CODEC12",
                    sent ? "QUEUED_COMMAND_SENT" : "QUEUED_COMMAND_SEND_FAILED",
                    "Queued Teltonika Codec12 command sent: "
                            + command
                            + " hex="
                            + hex,
                    null,
                    payload.length
            );

            System.out.println(
                    "[QUEUED COMMAND BUILD] TELTONIKA CODEC12 HEX = " + hex
            );

            System.out.println(
                    "[QUEUED COMMAND SEND] route=TELTONIKA_CODEC12 sent="
                            + sent
                            + " command="
                            + command
            );

            return sent;
        }

        System.out.println(
                "[QUEUED COMMAND SEND] failed: unknown route="
                        + routeName
                        + " imei="
                        + imei
        );

        TcpLogRepository.insert(
                imei,
                null,
                null,
                routeName,
                "QUEUED_COMMAND_SEND_FAILED",
                "Unknown queued command route=" + routeName + " command=" + command,
                null,
                null
        );

        return false;
    }
}
