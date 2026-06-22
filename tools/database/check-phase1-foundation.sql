\echo 'ALELS Phase-1 Foundation Check'
\echo '1) table count'
SELECT COUNT(*) AS public_table_count FROM pg_tables WHERE schemaname='public';
\echo '2) partitioned runtime tables'
SELECT c.relname AS table_name
FROM pg_partitioned_table pt
JOIN pg_class c ON c.oid = pt.partrelid
WHERE c.relname IN ('telemetry','raw_packets','tcp_logs')
ORDER BY c.relname;
\echo '3) partition status'
SELECT * FROM v_alels_partition_status ORDER BY parent_table, partition_month LIMIT 60;
\echo '4) key runtime tables'
SELECT tablename
FROM pg_tables
WHERE schemaname='public'
  AND tablename IN (
    'telemetry_ingestion_dlq','dlq_replay_jobs','ingestion_pipeline_health',
    'kafka_consumer_lag_snapshots','command_queue','command_responses',
    'device_presence_cache_shadow','device_latest_position','data_retention_policies',
    'partition_maintenance_runs','alels_runtime_settings'
  )
ORDER BY tablename;
\echo '5) pipeline health'
SELECT * FROM v_alels_pipeline_health;
\echo '6) retention policy'
SELECT * FROM data_retention_policies ORDER BY table_name;
