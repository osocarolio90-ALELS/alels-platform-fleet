package com.alels.ingestion.consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ConsumerCommitContractTest {
    @Test
    void offsetsCommitOnlyAfterBatchAndDurableFailureHandlingReturn() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/alels/ingestion/consumer/TelemetryConsumerService.java"));
        int process=source.indexOf("processBatch(records, deadLetterPublisher)");
        int commit=source.indexOf("consumer.commitSync()",process);
        assertTrue(process>=0&&commit>process);
    }

    @Test
    void autoCommitIsDisabledAndReadCommittedIsRequired() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/alels/ingestion/consumer/TelemetryConsumerService.java"));
        assertTrue(source.contains("ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, \"false\""));
        assertTrue(source.contains("ConsumerConfig.ISOLATION_LEVEL_CONFIG, \"read_committed\""));
    }
}
