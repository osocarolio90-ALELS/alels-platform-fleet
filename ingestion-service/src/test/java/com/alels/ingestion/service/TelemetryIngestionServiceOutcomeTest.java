package com.alels.ingestion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.repository.BatchIngestionResult;
import com.alels.ingestion.repository.TelemetryRepository;

class TelemetryIngestionServiceOutcomeTest {
    private TelemetryRepository repository;
    private TelemetryIngestionService service;

    @BeforeEach
    void setUp() {
        repository=mock(TelemetryRepository.class);
        service=new TelemetryIngestionService(repository);
    }

    @Test
    void validNeighborCommitsWhileOnlyInvalidRecordIsReportedForDlq() {
        IngestionRecord valid=record(10,"""
                {"imei":"123456789012345","protocol":"ALELS_JSON","channel":"WIFI","latitude":0,"longitude":0}
                """);
        IngestionRecord invalid=record(11,"{broken");
        when(repository.insertBatch(anyList(),anyList())).thenReturn(new BatchIngestionResult(1,0,0,List.of()));

        BatchIngestionResult result=service.ingestBatch(List.of(valid,invalid));

        assertEquals(1,result.processed);
        assertEquals(1,result.failed);
        assertEquals(invalid,result.failures.getFirst().record());
        ArgumentCaptor<List<IngestionRecord>> accepted=ArgumentCaptor.captor();
        verify(repository).insertBatch(accepted.capture(),anyList());
        assertEquals(List.of(valid),accepted.getValue());
    }

    @Test
    void databaseFailureEscapesSoKafkaOffsetCannotBeCommittedAsSuccess() {
        IngestionRecord valid=record(12,"""
                {"imei":"123456789012345","protocol":"ALELS_JSON","channel":"WIFI","latitude":0,"longitude":0}
                """);
        when(repository.insertBatch(anyList(),anyList())).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,()->service.ingestBatch(List.of(valid)));
    }

    private IngestionRecord record(long offset,String payload) {
        return new IngestionRecord("telemetry.raw",0,offset,"123456789012345",payload);
    }
}
