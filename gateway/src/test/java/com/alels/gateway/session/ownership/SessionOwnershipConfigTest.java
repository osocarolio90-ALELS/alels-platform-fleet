package com.alels.gateway.session.ownership;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionOwnershipConfigTest {
    @Test
    void singleReplicaDoesNotRequireCoordinator() {
        assertDoesNotThrow(() -> new SessionOwnershipConfig(1, "", 15, "owner:"));
    }

    @Test
    void multipleReplicasFailClosedWithoutCoordinator() {
        assertThrows(IllegalArgumentException.class,
                () -> new SessionOwnershipConfig(2, "", 15, "owner:"));
    }

    @Test
    void multipleReplicasAcceptSecureRedisEndpoint() {
        assertDoesNotThrow(() -> new SessionOwnershipConfig(
                2, "rediss://gateway-session.internal:6379", 15, "owner:"
        ));
    }
}
