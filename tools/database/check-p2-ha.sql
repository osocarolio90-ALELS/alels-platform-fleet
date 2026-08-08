\set ON_ERROR_STOP on

DO $$
DECLARE
    archive_setting text;
    sender_setting integer;
    streaming_standbys integer;
BEGIN
    SELECT setting INTO archive_setting FROM pg_settings WHERE name = 'archive_mode';
    SELECT setting::integer INTO sender_setting FROM pg_settings WHERE name = 'max_wal_senders';
    SELECT count(*) INTO streaming_standbys
    FROM pg_stat_replication
    WHERE state = 'streaming';

    IF pg_is_in_recovery() THEN
        RAISE EXCEPTION 'P2 HA check must run against the writable primary';
    END IF;
    IF archive_setting <> 'on' AND archive_setting <> 'always' THEN
        RAISE EXCEPTION 'PITR is not ready: archive_mode=%', archive_setting;
    END IF;
    IF sender_setting < 2 THEN
        RAISE EXCEPTION 'HA is not ready: max_wal_senders=% (minimum 2)', sender_setting;
    END IF;
    IF streaming_standbys < 1 THEN
        RAISE EXCEPTION 'HA is not ready: no streaming standby is connected';
    END IF;
END $$;

SELECT application_name, client_addr, state, sync_state,
       pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn)) AS replay_lag
FROM pg_stat_replication
ORDER BY application_name;

SELECT archived_count, failed_count, last_archived_time, last_failed_time
FROM pg_stat_archiver;
