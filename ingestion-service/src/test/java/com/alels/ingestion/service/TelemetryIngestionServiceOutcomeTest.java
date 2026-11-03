package com.alels.ingestion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.alels.ingestion.model.TelemetryMessage;

import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.repository.BatchIngestionResult;
import com.alels.ingestion.repository.TelemetryRepository;

class TelemetryIngestionServiceOutcomeTest {
    private FakeRepository repository;
    private TelemetryIngestionService service;

    @BeforeEach
    void setUp() {
        repository=new FakeRepository();
        service=new TelemetryIngestionService(repository);
    }

    @Test
    void validNeighborCommitsWhileOnlyInvalidRecordIsReportedForDlq() {
        IngestionRecord valid=record(10,"""
                {"imei":"123456789012345","protocol":"ALELS_JSON","channel":"WIFI","latitude":0,"longitude":0}
                """);
        IngestionRecord invalid=record(11,"{broken");
        repository.result = new BatchIngestionResult(1,0,0,List.of());

        BatchIngestionResult result=service.ingestBatch(List.of(valid,invalid));

        assertEquals(1,result.processed);
        assertEquals(1,result.failed);
        assertEquals(invalid,result.failures.getFirst().record());
        assertEquals(List.of(valid),repository.accepted);
    }

    @Test
    void databaseFailureEscapesSoKafkaOffsetCannotBeCommittedAsSuccess() {
        IngestionRecord valid=record(12,"""
                {"imei":"123456789012345","protocol":"ALELS_JSON","channel":"WIFI","latitude":0,"longitude":0}
                """);
        repository.fail = true;

        assertThrows(IllegalStateException.class,()->service.ingestBatch(List.of(valid)));
    }

    private IngestionRecord record(long offset,String payload) {
        return new IngestionRecord("telemetry.raw",0,offset,"123456789012345",payload);
    }

    private static class FakeRepository extends TelemetryRepository {
        BatchIngestionResult result;
        List<IngestionRecord> accepted;
        boolean fail;
        FakeRepository() { super(null); }
        @Override public BatchIngestionResult insertBatch(List<IngestionRecord> records,
                                                          List<TelemetryMessage> messages) {
            accepted = records;
            if (fail) throw new IllegalStateException("database unavailable");
            return result;
        }
    }
}
