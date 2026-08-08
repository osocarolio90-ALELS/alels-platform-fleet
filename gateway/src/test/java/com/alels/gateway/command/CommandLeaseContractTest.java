package com.alels.gateway.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommandLeaseContractTest {
    @Test
    void multiGatewayPollerFiltersOwnershipAndClaimsBeforeSending() throws Exception {
        String poller = Files.readString(Path.of(
                "src/main/java/com/alels/gateway/poller/PendingCommandPoller.java"
        ));
        int ownership = poller.indexOf("CellRoutingConfig.current().owns(command.imei)");
        int activeSession = poller.indexOf("CommandDispatcher.hasActiveChannel(command.imei)");
        int lease = poller.indexOf("CommandQueueRepository.tryLease");
        int send = poller.indexOf("CommandDispatcher.sendQueuedCommand");
        int complete = poller.indexOf("CommandQueueRepository.completeLease");
        assertTrue(ownership >= 0 && ownership < activeSession);
        assertTrue(activeSession < lease && lease < send && send < complete);
    }

    @Test
    void leaseCompletionIsFencedByOwner() throws Exception {
        String repository = Files.readString(Path.of(
                "src/main/java/com/alels/gateway/repository/CommandQueueRepository.java"
        ));
        assertTrue(repository.contains("status = 'SENDING' AND lease_owner = ?"));
        assertTrue(repository.contains("attempt_count = attempt_count + 1"));
        assertTrue(repository.contains("attempt_count >= max_attempts"));
    }
}
