package com.alels.gateway.poller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.alels.gateway.command.CommandDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PendingCommandPoller implements Runnable {

    private static final String API_URL =
            "http://localhost:8080/api/commands/pending?limit=100";

    private static final String COMMAND_API_BASE_URL =
            "http://localhost:8080/api/commands";

    private final ObjectMapper mapper =
            new ObjectMapper();

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    @Override
    public void run() {

        while (true) {

            try {

                pollPendingCommands();

                Thread.sleep(5000);

            } catch (Exception e) {

                System.err.println("[PENDING COMMAND POLLER ERROR]");
                e.printStackTrace();

                sleepQuietly();
            }
        }
    }

    private void pollPendingCommands() throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {

            System.out.println(
                    "[PENDING COMMAND POLLER] API status="
                            + response.statusCode()
                            + " body="
                            + response.body()
            );

            return;
        }

        String body = response.body();

        if (body == null || body.isBlank()) {
            return;
        }

        PendingCommandDto[] commands;

        try {
            commands =
                    mapper.readValue(
                            body,
                            PendingCommandDto[].class
                    );
        } catch (Exception e) {
            System.err.println("[PENDING COMMAND POLLER JSON ERROR]");
            System.err.println("BODY=" + body);
            e.printStackTrace();
            return;
        }

        if (commands == null || commands.length == 0) {
            return;
        }

        for (PendingCommandDto cmd : commands) {
            processCommand(cmd);
        }
    }

    private void processCommand(
            PendingCommandDto cmd
    ) {

        if (cmd == null || cmd.id == null) {
            return;
        }

        try {

            System.out.println(
                    "[PENDING COMMAND] "
                            + "id=" + cmd.id
                            + " imei=" + cmd.imei
                            + " route=" + cmd.route
                            + " command=" + cmd.commandName
            );

            if (!CommandDispatcher.hasActiveChannel(cmd.imei)) {
                System.out.println(
                        "[PENDING COMMAND] device offline, keep pending id="
                                + cmd.id
                                + " imei="
                                + cmd.imei
                );
                return;
            }

            boolean sent =
                    CommandDispatcher.sendQueuedCommand(
                            cmd.imei,
                            cmd.commandName,
                            cmd.route
                    );

            updateStatus(
                    cmd.id,
                    sent
                            ? "sent"
                            : "failed"
            );

        } catch (Exception e) {

            System.err.println("[PENDING COMMAND ERROR] id=" + cmd.id);
            e.printStackTrace();

            updateStatus(
                    cmd.id,
                    "failed"
            );
        }
    }

    private void updateStatus(
            Long commandId,
            String action
    ) {

        try {

            String url =
                    COMMAND_API_BASE_URL
                            + "/"
                            + commandId
                            + "/"
                            + action;

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .POST(
                                    HttpRequest.BodyPublishers.noBody()
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            System.out.println(
                    "[COMMAND STATUS UPDATE] id="
                            + commandId
                            + " action="
                            + action
                            + " statusCode="
                            + response.statusCode()
                            + " body="
                            + response.body()
            );

        } catch (Exception e) {

            System.err.println("[COMMAND STATUS UPDATE ERROR] id=" + commandId);
            e.printStackTrace();
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(5000);
        } catch (Exception ignored) {
        }
    }
}
