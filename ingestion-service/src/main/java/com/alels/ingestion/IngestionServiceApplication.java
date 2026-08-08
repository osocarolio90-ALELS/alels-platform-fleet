package com.alels.ingestion;

import com.alels.ingestion.config.IngestionConfig;
import com.alels.ingestion.consumer.TelemetryConsumerService;
import com.alels.ingestion.repository.Database;
import com.alels.ingestion.repository.TelemetryRepository;
import com.alels.ingestion.service.TelemetryIngestionService;

public class IngestionServiceApplication {

    public static void main(String[] args) {
        IngestionConfig config =
                IngestionConfig.load();

        Database database =
                new Database(config);

        TelemetryRepository telemetryRepository =
                new TelemetryRepository(database);

        TelemetryIngestionService ingestionService =
                new TelemetryIngestionService(telemetryRepository);

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(database::close)
                );

        TelemetryConsumerService consumerService =
                new TelemetryConsumerService(
                        config,
                        ingestionService
                );

        consumerService.start();
    }
}
