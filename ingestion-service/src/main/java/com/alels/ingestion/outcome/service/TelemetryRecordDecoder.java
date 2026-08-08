package com.alels.ingestion.outcome.service;

import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.model.TelemetryMessage;
import com.alels.ingestion.outcome.model.DecodedTelemetryBatch;
import com.alels.ingestion.outcome.model.IngestionFailure;
import com.alels.ingestion.validation.service.TelemetryMessageValidator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class TelemetryRecordDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TelemetryRecordDecoder() {
    }

    public static DecodedTelemetryBatch decode(List<IngestionRecord> input) {
        List<IngestionRecord> records = new ArrayList<>();
        List<TelemetryMessage> messages = new ArrayList<>();
        List<IngestionFailure> failures = new ArrayList<>();

        for (IngestionRecord record : input) {
            if (record == null || record.payload == null || record.payload.isBlank()) {
                failures.add(new IngestionFailure(record, "payload is required"));
                continue;
            }
            try {
                TelemetryMessage message = MAPPER.readValue(record.payload, TelemetryMessage.class);
                String validationError = TelemetryMessageValidator.validate(message);
                if (validationError != null) {
                    failures.add(new IngestionFailure(record, validationError));
                    continue;
                }
                records.add(record);
                messages.add(message);
            } catch (Exception invalidJson) {
                failures.add(new IngestionFailure(record, "invalid telemetry JSON"));
            }
        }

        return new DecodedTelemetryBatch(records, messages, failures);
    }
}
