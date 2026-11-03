# Canonical Identity v1 preparation — Phase C2-R1C

Owner: gateway and ingestion maintainers. Status: design awaiting explicit implementation approvals.
Scope: documentation and isolated compatibility tests only. No persistence or deduplication behavior is activated.

## Evidence and limitations

- Codec 8 and 8E retain the source timestamp in milliseconds and produce `packetSequence = timestampMs * 256 + oneBasedPacketRecordIndex`. Priority, event IO, GPS and IO values remain available. Original record byte boundaries and an explicit record-index field do not survive in `TelemetryData` today.
- The record index restarts in every packet. Repacking an existing record can change its derived sequence. Identical independent packets can have the same timestamp, index and every byte. Neither derived sequence nor byte equality establishes physical-event identity.
- ALELS uses A (IMEI), Q, B (UTC text), K (event IO), S (record size) and ordinary IO fields. T identifies record type. S is reserved by the normalizer and is not an identity. The current normalizer defaults missing Q to zero and missing B to an empty string; classification therefore needs evidence of presence from the original JSON.
- The inspected, untracked firmware saves Q in Preferences. A normal power cycle loads Q; it does not inherently reset Q. Explicit reset_q and format reset it. The firmware converts milliseconds to whole seconds for B. The inspected file is evidence about this implementation, not proof of the firmware deployed on every device.
- The queue writes/flushed JSON to SD before incrementing Q in a separate Preferences operation. There is no atomic SD/NVS commit or verified power-loss event-ID protocol. The interval between saving a record and saving the next Q can reuse Q after recovery. File.flush and the print byte count alone are not proof of physical power-loss durability.
- No verified source session/epoch or globally unique source event ID is present in the current data contract. An identifier-looking string from an unverified sender is not sufficient for STRONG classification.
- Gateway raw audit IDs change per reception. Kafka messages include gatewayRawPacketId. Ingestion's general delivery hash includes it, whereas BLE's hash removes it. Direct BLE hashing uses raw packet bytes and a different preimage. These existing behaviors are not an activated v1 contract.
- Kafka offset claims are nonpartitioned and committed with ingestion writes. Their existing consumer-group scope must remain intact. Topic/partition/offset identity assumes the same Kafka cluster and topic incarnation; topic recreation, another cluster, guard cleanup and another consumer group are outside the current guarantee.
- Devices are identified by a registered IMEI, company/device IDs by database ownership. Current general telemetry lacks a persisted company/device identity namespace; BLE resolves the gateway's current company. An ownership change during queuing must never silently reassign an old company's message to a new company.

## Contract

The optional internal Kafka envelope member is `canonicalIdentity`. It does not change the device JSON or Codec formats. Existing measurement fields remain intact.

| Field | Type and meaning |
| --- | --- |
| contractVersion | Integer 1; exact version, no implicit reinterpretation |
| companyId | Verified positive BIGINT encoded as decimal string; null if unresolved |
| deviceId | Verified positive BIGINT encoded as decimal string; null if unresolved |
| imei | Exact validated 15 ASCII digits; must agree with admitted device |
| sourceProtocol | Existing source protocol enum; preserve separate namespaces for ALELS_JSON, TELTONIKA_CODEC8 and TELTONIKA_CODEC8E |
| sourceEvent | null, or `{scheme, value}` where scheme names a verified source allocation guarantee and value is a bounded canonical identifier |
| confidence | STRONG, CONDITIONAL, AMBIGUOUS or MISSING |
| transportDelivery | Gateway reception reference: raw-audit store namespace, raw ID, receivedAt and transport. The Kafka consumer separately attaches its actual delivery coordinates; a producer cannot predict its offset |
| rawPacketAudit | Audit store namespace, raw ID as decimal string, receivedAt UTC timestamp; refer to existing durable audit evidence |
| sourceRecordIndex | Explicit zero-based ordinal in this received frame; ALELS data JSON is 0. null if unavailable; never inferred from a missing Q |
| diagnosticFingerprint | null, or `{scheme: "sha256-wire-record-v1", value: lowerHexDigest}` of original record bytes; diagnostic only |

STRONG requires verified source retry stability and uniqueness, an admitted tenant/device namespace and a supported source-event scheme. No current Q, synthetic packetSequence, gateway receipt UUID or raw audit ID becomes STRONG. CONDITIONAL records have valid timestamp/sequence/record evidence but no verified source ID. AMBIGUOUS includes demonstrated reuse or conflicting evidence; identical wire data with no session guarantee remains incapable of proving physical-event equality even if labelled CONDITIONAL. MISSING means required source/namespace evidence is absent. In all three non-STRONG classes, fingerprints cannot suppress independent deliveries.

Missing/invalid identity metadata must be surfaced and must not fabricate tenant, event ID, timestamp precision or source index. It must not turn valid ordinary telemetry into a duplicate. Existing device-admission and tenant restrictions still apply; unresolved ownership is not permission to write to another tenant.

### Deterministic bytes and namespaces

JSON carries metadata; property ordering of received JSON does not determine equality. The producer writes fields in the table order, compact UTF-8 JSON, decimal strings without leading zeros, explicit nulls, canonical lower-case UUID text for a UUID source scheme and UTC timestamps with Z. Raw source fields stay unmodified. Source-event schemes define normalization; arbitrary identifier case-folding is forbidden.

The generator's strong-key preimage is the fixed-order tuple:

`("alels-source-event-v1", companyId, deviceId, sourceProtocol, sourceEvent.scheme, sourceEvent.value)`.

Each string is UTF-8 with a four-byte unsigned big-endian byte-length prefix. Null uses length 0xffffffff; an empty string has length zero. Required strong-key components cannot be null/empty. SHA-256 is encoded as 64 lower-case hex characters. Audit IDs, channel, timestamps, Kafka coordinates, labels, dictionary versions, computed BLE results and gateway cell metadata do not participate. A content digest is a consistency check, not the event ID. A natural-key uniqueness constraint and comparison of the stored namespace prevent treating a hash collision as equivalence.

Version 1 serialization is immutable. Future versions require an explicit translation/claim compatibility plan; changing a version must not allow an already claimed source event to be inserted again. The claim's natural source namespace is version independent. The source protocol namespace is retained: equivalence of an ALELS Hub event and a native Teltonika event is not assumed.

### Delivery identities and guarantees

TransportDeliveryId identifies one frame reception. No stable explicit transport ID is currently emitted; the durable audit reference supplies a receipt namespace after audit persistence. RawPacketAuditId identifies that audit row, not a source measurement. Include the audit store and receivedAt because raw_packets is partitioned without a global ID constraint. Exact Kafka redelivery uses the consumer's Kafka coordinates and existing offset guard. A delivery with multiple records adds the record ordinal to its delivery-record identity.

Only a proven source ID supports physical-event deduplication across new TCP receptions or pipeline changes. In all other cases, a new reception is preserved, including identical readings, repeated times, reused Q and different Kafka offsets. This permits duplicate physical events when a device retries without a strong ID; preventing data loss takes priority. Reusing the same already persisted delivery/record reference remains idempotent. Re-executing after losing that reference is a new delivery unless strong source evidence exists.

## Future ALELS extension and storage protocol

Proposal, not implemented: optional scalar `sourceEventId`, canonical UUID text backed by a verified allocation/storage scheme. Existing T/Q/A/B/K/S and serial commands retain their meanings. Deploy receiver support and reserve this exact field before any firmware emits it: today an unknown field enters ioData. Ignoring an unknown field is not established end-to-end compatibility. Accept legacy JSON without the field using non-STRONG policy. A malformed/unsupported ID remains traceable and cannot enable source deduplication.

Allocate one ID when a physical record is accepted for durable queuing, before its first transmission. Store that ID inside the queued record. Retry must read the stored ID; never generate it in the send loop. Normal reboot, reset_q and Q reuse do not rewrite IDs in existing queue entries; a newly accepted event gets a new ID regardless of Q.

Future firmware approval must include a durable enqueue protocol: versioned entry with ID, content checksum and explicit commit record; recovery enumerates only validated committed entries and retains their original IDs. An allocator must not reuse an allocated value after a crash (for example a durable, reserved monotonic allocation namespace/range with verified namespace uniqueness). Random UUID allocation gives a probabilistic uniqueness claim and must not be described as a mathematical guarantee. Namespace replacement/reset and counter exhaustion need an explicit recovery rule. No cross-store atomicity between SD and Preferences may be assumed. Torn writes, storage-controller caches, power failure before/after allocation, and failure before/after queue commit require hardware fault tests before the source scheme is approved as STRONG.

Current firmware storage semantics do not establish this guarantee. Its existing Q persistence cannot be upgraded to STRONG by server configuration alone. For Teltonika with no verified source ID, use CONDITIONAL/AMBIGUOUS evidence and preserve new deliveries; no vendor packet-format change is proposed.

## Component and flow design

The existing publisher boundary is suitable: both `KafkaTelemetryPublisher.publish` and `DirectDatabaseTelemetryPublisher.publish` already normalize the same TelemetryData. Use one pure gateway `TelemetryIdentityFactory` in `com.alels.gateway.service`. Both publishers call this one implementation with the same immutable context; ingestion consumes its metadata rather than implementing another generator. Only one publisher mode runs for a publication.

Proposed signatures (not production code):

```java
record SourceRecordEvidence(Integer sourceRecordIndex, byte[] wireRecordBytes,
                            boolean sequencePresent, String originalSequence,
                            String originalTimestamp, SourceEventEvidence sourceEvent) {}
record TelemetryPublicationContext(VerifiedDeviceNamespace namespace,
                                   RawPacketAuditReference audit,
                                   SourceRecordEvidence source) {}
record CanonicalTelemetryIdentity(/* fields defined above */) {}
final class TelemetryIdentityFactory {
    static CanonicalTelemetryIdentity create(TelemetryPublicationContext context);
}
```

The records live in the existing gateway model package. Constructors defensively copy byte arrays. VerifiedDeviceNamespace is captured from server admission, not supplied by the device. The factory performs no DB I/O and no deduplication. SourceEventEvidence includes a verified source-scheme capability, not a user-controlled STRONG flag.

1. Existing parser loops capture byte offsets and ordinals while decoding; no second decoder is added and existing telemetry values/packetSequence semantics remain unchanged. ALELS evidence is extracted from the existing JSON read and distinguishes absent, zero and malformed fields.
2. Audit persistence returns its existing ID plus receivedAt/store reference. Admission supplies companyId/deviceId/IMEI as one snapshot. A missing context is explicit, not an invented reference.
3. Existing ALELS publication and TelemetryBatchPublisher pass immutable context alongside TelemetryData. Each record has its own ordinal while the packet audit reference is shared.
4. Both existing publishers invoke the same factory after source evidence capture; normalization must not alter source evidence. Kafka adds the optional envelope member. Direct DB passes the same metadata to its repository.
5. Ingestion adds a DTO and validation only. It checks supported version/scheme, types, IMEI/key agreement, admission namespace and authorization; it does not trust arbitrary confidence or recalculate a different source algorithm. For required key verification, one database claim implementation derives/verifies the supplied natural key for both paths. Unknown versions cannot activate strong deduplication.
6. Persistence verifies/locks device ownership inside the writing transaction. A stale queued namespace is quarantined/retried through established failure handling, not silently mapped to a new company. Persist actual delivery coordinates and source lineage separately from source-event identity.
7. BLE takes the selected persisted record key from persistence. It does not generate its own identity from parser output or envelope serialization.

## Persistent claim proposal (report only)

The existing Kafka offset guard remains unchanged for its current consumer-group scoped purpose. It cannot be reused for source-event IDs by overloading topic or offset fields.

Proposed new nonpartitioned table; no migration number selected and no SQL file created:

```sql
CREATE TABLE telemetry_source_identity_claims (
    company_id BIGINT NOT NULL REFERENCES companies(id),
    device_id BIGINT NOT NULL,
    imei VARCHAR(32) NOT NULL,
    source_protocol VARCHAR(80) NOT NULL,
    source_identity_scheme VARCHAR(40) NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    identity_contract_version SMALLINT NOT NULL CHECK (identity_contract_version = 1),
    logical_record_key CHAR(64) NOT NULL UNIQUE,
    source_content_digest CHAR(64) NOT NULL,
    telemetry_id BIGINT NOT NULL,
    telemetry_server_time TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (company_id, device_id, source_protocol, source_identity_scheme, source_event_id),
    FOREIGN KEY (device_id, company_id) REFERENCES devices(id, company_id)
);
```

Only verified STRONG events enter this table; the primary key deliberately excludes contractVersion. Use bounded, ASCII identifiers under a deterministic collation. Verify those constraints in the eventual migration and source-scheme validators. No foreign key is proposed to partitioned telemetry(id); the stored ID/server_time pair supplies lineage. Device/company deletion uses restrictive behavior until a reviewed retention policy specifies otherwise; do not cascade away dedup evidence silently. The composite devices uniqueness dependency from 026 must be verified before deployment.

A source claim cannot also represent all the distinct audit receipts that resolve to it. The current schema has no many-deliveries-to-one-record association. To preserve that lineage and protect retries of the same direct receipt for non-STRONG records, the minimum complete proposal additionally needs the following delivery association. It also persists version/confidence metadata without changing telemetry's measurement columns:

```sql
CREATE TABLE telemetry_delivery_records (
    company_id BIGINT NOT NULL REFERENCES companies(id),
    device_id BIGINT NOT NULL,
    delivery_record_key CHAR(64) NOT NULL,
    identity_contract_version SMALLINT NOT NULL CHECK (identity_contract_version = 1),
    identity_confidence VARCHAR(16) NOT NULL
        CHECK (identity_confidence IN ('STRONG','CONDITIONAL','AMBIGUOUS','MISSING')),
    identity_metadata JSONB NOT NULL CHECK (jsonb_typeof(identity_metadata) = 'object'),
    telemetry_id BIGINT NOT NULL,
    telemetry_server_time TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (company_id, device_id, delivery_record_key),
    FOREIGN KEY (device_id, company_id) REFERENCES devices(id, company_id)
);
```

The delivery key uses the length-framed, versioned Kafka/direct reference tuples below. Metadata retains actual audit/Kafka references and source ordinal; index additional retrieval fields only after query-plan evidence. Both tables are proposals requiring approval; no live or fixture schema is changed here. Neither table supplies missing physical-event evidence. The shared write function validates metadata against typed namespace fields. On a duplicate delivery key it verifies the reference tuple, not just the digest, and returns the existing telemetry reference. A different strong delivery can map to the same telemetry through a new association row. Distinct ambiguous deliveries map to distinct telemetry rows.

Claim lifecycle contains no committed pending row. In the existing transaction: validate namespace, claim the Kafka offset where applicable, check an existing delivery association, create a savepoint, insert the prospective telemetry row and obtain its ID/server_time, then insert the source claim for STRONG events with ON CONFLICT DO NOTHING RETURNING. A winner inserts its delivery association, writes required BLE/IO data and commits. A source-claim loser rolls back to the savepoint (removing its prospective telemetry), reads the winner claim and verifies natural key/content, then inserts its new delivery association to the winner. A delivery-association conflict likewise rolls back speculative writes and resolves the existing delivery; it never merges two different ambiguous deliveries. A new Kafka delivery offset can be committed as handled without adding another source event. A mismatching digest for the same strong natural ID is an integrity conflict, never a silent duplicate.

For the proposed certified queued-JSON source scheme, source_content_digest is SHA-256 of the immutable committed JSON object bytes (including sourceEventId), excluding only transport CR/LF framing. Queue resend must preserve internal whitespace, field ordering and content bytes. Native codec diagnostic digests use the exact timestamp-through-last-IO byte slice; no native source scheme is currently STRONG. Enrichment, audit IDs and Kafka metadata never enter either source-content digest. A future source scheme that permits source reserialization needs its own content-consistency rule and certification rather than silently changing this rule.

PostgreSQL uniqueness serializes concurrent claim/association attempts; a rolled-back winner allows another contender to claim. Process keys in deterministic order in batches to reduce deadlocks; database deadlock/connection failures follow existing transaction retries. No automatic success occurs on an uncommitted claim. A failure before commit leaves no telemetry, claim, association or BLE writes. A failure after commit is recovered by the offset guard, delivery association or strong source claim. Audit receptions remain separately durable, including losing deliveries. Failure to retain a required delivery association prevents activation.

Direct mode currently commits telemetry/BLE before later IO, normalization and alert calls. A shared source claim must not suppress recovery of partially completed required writes. Before activation, establish which existing writes are required and either include their connection-aware writes in the same transaction or prove their durable idempotent completion path. This is a scoped activation dependency; the current phase changes no boundaries or ACK timing. Kafka's TCP ACK continues to mean durable publication under its existing contract, not completion of consumer persistence.

Claims and telemetry must have coordinated retention: removing claims early reopens duplicates; retaining claims after removing telemetry leaves a claimed event without its historical result. Define an explicit supported replay horizon or retained tombstone policy before activation. The current gate provides no indefinite exactly-once claim.

## BLE record key selection

- STRONG: the shared logical_record_key above.
- CONDITIONAL/AMBIGUOUS/MISSING with Kafka: SHA-256 of length-framed `(alels-kafka-record-v1, tenant, device, configuredKafkaStreamNamespace, topic, partition, offset, sourceRecordIndex)`. Stream namespace identifies a stable cluster/topic incarnation; never infer it from a broker hostname. Same offset within that namespace and guard retention gives the same key; a different offset remains separate.
- Non-STRONG direct: SHA-256 of length-framed `(alels-direct-record-v1, tenant, device, auditStoreNamespace, rawPacketId, auditReceivedAt, sourceRecordIndex)`. Re-executing the same persisted audit/ordinal gives the same key. A new TCP receipt/audit gets a different key. If no durable receipt reference exists, use a persisted telemetry ID/server_time pair; do not generate a new random key on every transaction attempt. Normal production paths audit first, so this fallback is an explicit legacy limitation.

Existing `(company_id, source_record_key, io_id, block_index)` uniqueness is preserved. All source-record keys include a versioned namespace. The parser's per-IO zero-based beacon indexes and the separately approved negative error-index candidate remain independent of identity confidence. No parser JSON shape is changed. Two blocks with the same beacon ID in one record remain two observations.

Keep existing observations and source keys immutable. Do not backfill strong identity from old fingerprints or rekey history in place. Exact Kafka offsets already claimed remain skipped. Replaying older history under a new key can create additional observations; require a bounded cutover/checkpoint policy, version provenance, and review of source-key lookup indexes before any historical reprocessing. A source-key version must be auditable in persisted identity metadata; old 64-character digests do not self-identify their algorithm.

## Exact future file impact

All paths below are plans, not implementation changes in C2-R1C.

| Path | Change and purpose |
| --- | --- |
| gateway/src/main/java/com/alels/gateway/model/CanonicalTelemetryIdentity.java | New immutable v1 DTO and confidence/reference types |
| gateway/src/main/java/com/alels/gateway/model/TelemetryPublicationContext.java | Immutable namespace, audit and source-record evidence |
| gateway/src/main/java/com/alels/gateway/service/TelemetryIdentityFactory.java | Single classification/serialization implementation |
| gateway/src/main/java/com/alels/gateway/parser/ParserResult.java | Add optional source-evidence list without changing existing values |
| gateway/src/main/java/com/alels/gateway/parser/TeltonikaCodec8Parser.java | Capture existing reader offsets and record ordinal only |
| gateway/src/main/java/com/alels/gateway/parser/TeltonikaCodec8EParser.java | Capture existing reader offsets and record ordinal only |
| gateway/src/main/java/com/alels/gateway/normalizer/AlelsJsonTelemetryNormalizer.java | Preserve presence evidence; reserve/extract future event field only after approval |
| gateway/src/main/java/com/alels/gateway/admission/model/DeviceAdmission.java | Add verified company/device namespace to current snapshot |
| gateway/src/main/java/com/alels/gateway/admission/repository/DeviceAdmissionRepository.java | Load namespace with existing admission query |
| gateway/src/main/java/com/alels/gateway/admission/service/DeviceAdmissionRegistry.java | Expose immutable admitted namespace |
| gateway/src/main/java/com/alels/gateway/repository/PacketAuditRepository.java | Return ID and actual receivedAt without changing raw lifecycle |
| gateway/src/main/java/com/alels/gateway/service/PacketAuditService.java | Carry extended audit reference |
| gateway/src/main/java/com/alels/gateway/netty/NettyDeviceChannelHandler.java | Pass context at existing publish boundary; ACK callbacks unchanged |
| gateway/src/main/java/com/alels/gateway/netty/NettyUdpDeviceHandler.java | Carry the same context for the existing UDP entry point |
| gateway/src/main/java/com/alels/gateway/service/TelemetryBatchPublisher.java | Assign source ordinal/evidence per record |
| gateway/src/main/java/com/alels/gateway/publisher/TelemetryPublisher.java | Add context to existing publication contract and update callers explicitly |
| gateway/src/main/java/com/alels/gateway/publisher/KafkaTelemetryPublisher.java | Consume shared identity; optional internal envelope metadata |
| gateway/src/main/java/com/alels/gateway/publisher/DirectDatabaseTelemetryPublisher.java | Consume same identity and shared atomic claim; remove separate BLE key algorithm at cutover |
| gateway/src/main/java/com/alels/gateway/publisher/LoggingTelemetryPublisher.java | Carry context in existing development mode |
| gateway/src/main/java/com/alels/gateway/repository/TelemetryRepository.java | Connection-aware claim result/lineage handling |
| ingestion-service/src/main/java/com/alels/ingestion/model/TelemetryMessage.java | Optional identity DTO; retain original payload |
| ingestion-service/src/main/java/com/alels/ingestion/validation/service/TelemetryMessageValidator.java | Validate metadata/tenant namespace; legacy handling |
| ingestion-service/src/main/java/com/alels/ingestion/repository/TelemetryRepository.java | Replace ambiguous delivery suppression only at approved cutover; preserve offset guard; consume one claim contract |
| database/migrations/(number pending approval) | Source claims, delivery associations, shared SQL write function and identity/version lineage support after schema-state verification |

The shared SQL claim function is the single persistence implementation called by both Java repositories. Its API takes verified namespace, strong natural key and prospective telemetry reference; it does not generate or certify source event IDs. Evaluate delivery association and persisted identity/version metadata against actual schema before finalizing the migration. No existing unrelated API module requires changes. Scope connection overloads in IO/normalization/alerts only if necessary to establish direct-mode atomic recovery; stop for review if this requires unrelated refactoring.

## Deterministic test plan

Owner: gateway/ingestion maintainers; priority P1, transaction/tenant/failure cases P0. Unit environment: Java 21, synthetic timestamps, no network/DB. Integration environment: disposable PostgreSQL and Kafka with empty schemas/topics; never existing ALELS data. Each case runs independently, uses explicit IDs, checks commit outcomes and cleans only its disposable resources. `b=4` means two 10500 blocks plus two 548 blocks; `e=2` means two structured errors. No counts are substituted for parser output in actual integration assertions.

| ID / input | Class | Expected telemetry / BLE count | Audit lineage and guarantee |
| --- | --- | --- | --- |
| CI01 Q reset then identical legacy Q/B/payload on new receipt | CONDITIONAL/AMBIGUOUS | 2 / 2b | Two audit receipts preserved; preservation guaranteed, physical duplicate detection unsupported |
| CI02 normal power-cycle reload and queued resend on new receipt | CONDITIONAL | 2 / 2b | Same stored source bytes, two receptions; no automatic STRONG promotion |
| CI03 future certified event ID survives power cycle/queued resend | STRONG | 1 / b | Two audits link to winner; guaranteed within certified allocation/replay horizon |
| CI04 Codec 8/8E repeated millisecond timestamps in one packet | CONDITIONAL | 2 / 2b | One packet audit, distinct ordinals; preserve both |
| CI05 identical codec record in two independent packets | AMBIGUOUS | 2 / 2b | Two audit references, no content-based drop |
| CI06 same codec record repacked at another ordinal | CONDITIONAL | 2 / 2b | Preserve both; packetSequence may change, no physical uniqueness claim |
| CI07 exact Kafka offset redelivery and consumer restart | Any | 1 / b | One offset guard and one persisted record; same stream/group and retained guard required |
| CI08 different Kafka offsets, identical legacy content | CONDITIONAL/AMBIGUOUS | 2 / 2b | Each offset and gateway audit stays traceable |
| CI09 different raw audit IDs with same certified source ID | STRONG | 1 / b | Audit count 2, claim count 1, duplicate lineage retained |
| CI10 independent identical readings with distinct certified source IDs | STRONG | 2 / 2b | Never merge by content digest |
| CI11 legacy multiple IO 10500/548 blocks | CONDITIONAL | 1 / b | Parent delivery key + distinct IO/block indexes, original HEX unchanged |
| CI12 valid blocks plus parser errors, then exact offset replay | Any | 1 / b+e | Every error/source index preserved; candidate SQL approval is a separate dependency |
| CI13 concurrent same offset | Any | 1 / b | One committed offset guard; losing transaction adds no writes |
| CI14 concurrent new deliveries with same strong source ID | STRONG | 1 / b | One source claim, two audits, both deliveries linked |
| CI15 concurrent identical legacy receipts | AMBIGUOUS | 2 / 2b | Both preserved; BLE source keys must differ |
| CI16 failure before transaction commit, retry same delivery | Any | 1 / b after retry | No orphan claim/telemetry/BLE before retry; raw audits separately retained |
| CI17 failure after commit before acknowledgment | STRONG or same Kafka offset | 1 / b | Replay returns existing source/offset outcome; TCP legacy new receipt remains ambiguous |
| CI18 same bytes across companies/devices | Any | 1 per namespace / b per namespace | No cross-tenant/device claim or BLE merge |
| CI19 out-of-order independent records | Any | 2 / 2b | Original event times retained; registry first_seen/last_seen use extrema |
| CI20 optional/malformed/unknown-version metadata and absent Q | MISSING/AMBIGUOUS | Preserve valid deliveries; no strong claim | Keep raw evidence and diagnostic error; normal tenant validation still required |
| CI21 Q rollover and source counter exhaustion | Legacy AMBIGUOUS / future scheme-specific | Preserve new legacy deliveries; future scheme must not reuse strong ID | Test signed-Java versus unsigned-firmware numeric boundary and allocator recovery separately |
| CI22 certified source ID reused with conflicting source content | Integrity conflict | No second source telemetry/BLE commit | Preserve second audit and structured conflict; never count it as a benign duplicate |

The added `CanonicalIdentityV1CompatibilityTest` executes current parser/normalizer evidence relevant to CI01/04/05/06/11/19/20. It does not test device power cycling, implement identity hashing, simulate persistence, certify future IDs or claim end-to-end outcomes. Remaining rows are future executable acceptance tests. Current ambiguity handling is approved policy, not behavior activated by this document.

### Executed evidence — 2026-10-28

| Working directory / command | Actual result |
| --- | --- |
| gateway / `mvn -q test` | Exit 0, 61 tests, zero failures/errors; includes all eight new compatibility tests |
| ingestion-service / `mvn -q test` | Exit 0, 15 tests, zero failures/errors |
| backend / `mvn -q '-Dtest=BleObservationServiceTest' test` | Exit 0, three tests, zero failures/errors |

Existing gateway tests exercise codec decode, ALELS decode, IO 10500/548, normalized BLE equivalence and publisher/ACK source contracts. The new tests additionally run both actual codec parsers with repeated times, multiple records and repacked records. Kafka/direct database persistence, actual TCP ACK timing, broker durability, source allocation power loss, claim concurrency and the proposed serialization have not been executed under this design. The current production factory/claim mechanism does not exist, so no unit result is presented as proof of its behavior. No broader backend/Mockito suite or SQL candidate rerun was necessary for the two added documentation/test files; previous environmental failures are not reclassified as passes. No database was accessed during C2-R1C.

## Deployment dependencies, approvals and rollback

1. Verify actual 026 deployment/schema before proposing any migration; preserve 026 and existing SQL candidate fixtures unchanged.
2. Approve optional internal Kafka envelope/context implementation and delivery-only handling of legacy/ambiguous events. Removing today's Q/hash suppression changes storage volume and requires integration and capacity evidence.
3. Approve a forward-only schema/claim and provenance design after checking actual data, partitions, tenant deletion, ownership transfer, replay horizon and direct-mode atomicity. Choose a migration number only at that time.
4. Deploy compatible consumers before metadata-producing gateways; keep source dedup inactive until both paths use the same tested claim and persisted key strategy. Older consumers ignore unknown metadata and retain old dedup behavior, so mixed-version operation cannot claim v1 guarantees. Rollback must not silently return to ambiguous fingerprint suppression; halt/queue affected processing during an incompatible downgrade.
5. Separately approve source extension and firmware/storage changes. Receiver rollout must precede firmware rollout. Test real power loss and allocator recovery before certifying STRONG. Teltonika remains conservative unless verified source evidence becomes available.
6. Do not reinterpret/backfill/delete historical data or claim rows automatically. Retain approved schema on application rollback and plan forward recovery. A correction of BLE error persistence remains a separately approved forward-only deployment action.

Final decision: REQUIRES APPROVAL. The v1 design is concrete, but activating it requires the stated schema/provenance, cross-pipeline transaction and optional source-extension approvals. No Phase D work is authorized here.
