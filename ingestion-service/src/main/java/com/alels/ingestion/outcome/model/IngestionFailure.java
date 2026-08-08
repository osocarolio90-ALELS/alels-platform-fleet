package com.alels.ingestion.outcome.model;

import com.alels.ingestion.model.IngestionRecord;

public record IngestionFailure(IngestionRecord record, String reason) {
}
