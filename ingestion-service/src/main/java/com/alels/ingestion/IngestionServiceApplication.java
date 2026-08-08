package com.alels.ingestion;

import com.alels.ingestion.config.IngestionConfig;
import com.alels.ingestion.consumer.TelemetryConsumerService;
import com.alels.ingestion.repository.Database;
import com.alels.ingestion.repository.TelemetryRepository;
import com.alels.ingestion.service.TelemetryIngestionService;
import com.alels.ingestion.observability.service.IngestionHealthServer;
import com.alels.ingestion.observability.service.IngestionRuntimeMetrics;

public class IngestionServiceApplication {

    public static void main(String[] args) throws Exception {
        IngestionConfig config =
                IngestionConfig.load();

        Database database =
                new Database(config);

        TelemetryRepository telemetryRepository =
                new TelemetryRepository(database);

        TelemetryIngestionService ingestionService =
                new TelemetryIngestionService(telemetryRepository);

        TelemetryConsumerService consumerService =
                new TelemetryConsumerService(
                        config,
                        ingestionService
                );
        IngestionRuntimeMetrics metrics = IngestionRuntimeMetrics.instance();
        int healthPort = Integer.parseInt(
                System.getenv().getOrDefault("ALELS_INGESTION_HEALTH_PORT", "9091")
        );
        String healthHost = System.getenv().getOrDefault("ALELS_INGESTION_HEALTH_HOST", "127.0.0.1");
        try (IngestionHealthServer healthServer = new IngestionHealthServer(healthHost, healthPort, metrics)) {
            healthServer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                metrics.markDraining();
                consumerService.stop();
                database.close();
            }, "ingestion-drain-hook"));
            consumerService.start();
        } finally {
            database.close();
        }
    }
}
