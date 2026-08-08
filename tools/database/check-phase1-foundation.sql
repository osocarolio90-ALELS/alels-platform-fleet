\set ON_ERROR_STOP on
\echo 'ALELS P0/P1 Runtime Readiness Check'
DO $$
DECLARE missing text[];
BEGIN
  SELECT array_agg(required_name) INTO missing
  FROM (VALUES
    ('telemetry'),('raw_packets'),('tcp_logs'),('kafka_processed_offsets'),
    ('telemetry_ingestion_dlq'),('ingestion_pipeline_health'),
    ('device_latest_position'),('data_retention_policies')
  ) required(required_name)
  WHERE to_regclass('public.'||required_name) IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Missing required P0/P1 tables: %', missing;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='users' AND column_name='session_last_seen_at'
  ) THEN
    RAISE EXCEPTION 'Migration 018 is not applied: users.session_last_seen_at is missing';
  END IF;

  IF to_regprocedure('alels_ensure_runtime_partitions(integer)') IS NULL
     OR to_regprocedure('alels_apply_retention(text,boolean)') IS NULL THEN
    RAISE EXCEPTION 'Partition lifecycle functions are missing';
  END IF;
END $$;
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
\echo '7) single active session schema'
SELECT column_name,data_type,is_nullable
FROM information_schema.columns
WHERE table_schema='public' AND table_name='users' AND column_name IN ('session_version','session_last_seen_at')
ORDER BY column_name;
\echo 'ALELS P0/P1 runtime readiness check passed'
