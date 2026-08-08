\set ON_ERROR_STOP on
\if :{?window_hours}
\else
\set window_hours 24
\endif

CREATE TEMP TABLE alels_reconciliation_result AS
SELECT
    count(*) AS processed_offsets,
    count(*) FILTER (WHERE r.kafka_offset IS NULL) AS missing_raw_packets,
    count(*) FILTER (WHERE t.kafka_offset IS NULL) AS missing_telemetry,
    (SELECT count(*) FROM (
        SELECT kafka_topic, kafka_partition, kafka_offset
        FROM raw_packets
        WHERE received_at >= NOW() - (:'window_hours' || ' hours')::interval
          AND kafka_topic IS NOT NULL
        GROUP BY kafka_topic, kafka_partition, kafka_offset
        HAVING count(*) > 1
    ) duplicate_offsets) AS duplicate_raw_offsets,
    (SELECT count(*) FROM (
        SELECT kafka_topic, kafka_partition, kafka_offset
        FROM telemetry
        WHERE created_at >= NOW() - (:'window_hours' || ' hours')::interval
          AND kafka_topic IS NOT NULL
        GROUP BY kafka_topic, kafka_partition, kafka_offset
        HAVING count(*) > 1
    ) duplicate_offsets) AS duplicate_telemetry_offsets
FROM kafka_processed_offsets p
LEFT JOIN raw_packets r
  ON r.kafka_topic = p.topic_name
 AND r.kafka_partition = p.partition_no
 AND r.kafka_offset = p.offset_no
LEFT JOIN telemetry t
  ON t.kafka_topic = p.topic_name
 AND t.kafka_partition = p.partition_no
 AND t.kafka_offset = p.offset_no
WHERE p.processed_at >= NOW() - (:'window_hours' || ' hours')::interval
  AND p.status = 'PROCESSED';

DO $$
DECLARE
    result record;
BEGIN
    SELECT * INTO result FROM alels_reconciliation_result;
    IF result.missing_raw_packets <> 0 OR result.missing_telemetry <> 0
       OR result.duplicate_raw_offsets <> 0 OR result.duplicate_telemetry_offsets <> 0 THEN
        RAISE EXCEPTION
            'Telemetry reconciliation failed: processed=%, missing_raw=%, missing_telemetry=%, duplicate_raw=%, duplicate_telemetry=%',
            result.processed_offsets, result.missing_raw_packets, result.missing_telemetry,
            result.duplicate_raw_offsets, result.duplicate_telemetry_offsets;
    END IF;
END $$;

TABLE alels_reconciliation_result;
