package com.alels.gateway.cell.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CellRoutingConfigTest {
    @Test
    void assignmentIsDeterministicAndBalanced() {
        CellRoutingConfig config = new CellRoutingConfig("cell-0", 0, 8, true, true);
        int[] counts = new int[8];
        for (int index = 0; index < 100_000; index++) {
            String imei = String.format("%015d", index);
            int first = config.assignedCell(imei);
            assertEquals(first, config.assignedCell(imei));
            counts[first]++;
        }
        for (int count : counts) assertTrue(count > 11_000 && count < 14_000);
    }

    @Test
    void multiCellCannotRunWithoutOwnershipAndTopicIsolation() {
        assertThrows(IllegalArgumentException.class,
                () -> new CellRoutingConfig("cell-0", 0, 2, false, true));
        assertThrows(IllegalArgumentException.class,
                () -> new CellRoutingConfig("cell-0", 0, 2, true, false));
    }

    @Test
    void singleCellPreservesTheExistingTopicAndOwnsEveryDevice() {
        CellRoutingConfig config = new CellRoutingConfig("cell-0", 0, 1, true, false);
        assertTrue(config.owns("123456789012345"));
        assertEquals("telemetry.raw", config.topic("telemetry.raw"));
    }
}
