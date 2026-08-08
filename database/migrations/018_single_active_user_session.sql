BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS session_last_seen_at TIMESTAMPTZ;

COMMENT ON COLUMN users.session_last_seen_at IS
    'Heartbeat for the single active browser session. A session may be replaced only after five seconds without activity.';

COMMIT;
