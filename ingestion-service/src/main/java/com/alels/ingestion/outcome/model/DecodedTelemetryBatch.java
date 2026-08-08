package com.alels.ingestion.outcome.model;

import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.model.TelemetryMessage;

import java.util.List;

public record DecodedTelemetryBatch(
        List<IngestionRecord> records,
        List<TelemetryMessage> messages,
        List<IngestionFailure> failures
) {
    public DecodedTelemetryBatch {
        records = List.copyOf(records);
        messages = List.copyOf(messages);
        failures = List.copyOf(failures);
    }
}
