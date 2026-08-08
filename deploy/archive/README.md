# P3 Telemetry Cold Archive

Cold storage consumes the durable, cell-specific `telemetry.raw.<cell-id>` topic independently from database ingestion. The archive is immutable and partitioned by event time; it is not used in the gateway acknowledgement boundary and therefore cannot delay device traffic.

Use a production Kafka Connect S3-compatible sink that supports the connector contract in `telemetry-cold-storage-connector.properties.example`. Supply credentials only through the runtime secret manager. Enable object-lock/retention and lifecycle rules in the bucket, monitor connector lag and failed tasks, and perform scheduled restore/replay exercises before declaring the archive operational.

Every cell uses a distinct connector name, topic and storage prefix. Do not share consumer groups with ingestion. Archive reconciliation compares broker offsets and object counts/checksums; deletion from hot storage is allowed only after the archive verification and retention policy pass.
