# BLE monitoring finalization — PARTIAL

Tanggal: 2 November 2026. Ini laporan perubahan pada sesi finalization, bukan seluruh diff terhadap HEAD. Worktree sebelumnya sudah berisi perubahan lain dan tetap dipertahankan.

## A. Ownership dan arsitektur

Audit awal: [ownership](BLE_PRODUCTION_OWNERSHIP_AUDIT.md). Satu EyeBeaconParser digunakan normalizer gateway dan read model backend. Received cards membaca telemetry durable; registry tetap sumber nama/type/MAC. Resolver membaca asosiasi terverifikasi opsional. Tidak ada parser/service duplikat, migration baru, atau location engine.

## B. Dukungan protokol

| IO | Perilaku |
|---|---|
| 10500 | Simple Eddystone dan iBeacon; identitas dan RSSI sesuai bytes |
| 548 | Parameterized atau legacy; struktur ambigu memerlukan metadata layout eksplisit; blok valid tetap dipertahankan saat blok lain rusak |
| 385 | Perlu bukti model/profile; FMB640/FMC640/FMM640/FMC650/FMM650 Manual CAN tidak dianggap BLE |
| 11317 | Model GH5200/TMT250/TFT100 terdokumentasi; temperature/roll ditahan bila endian belum terbukti |
| 108xx | 40 dedicated IDs sesuai kelompok slot; perlu identitas slot terverifikasi |
| 10828/10829/10831 | Dukungan legacy dipertahankan |

Codec 8 standar memakai AVL ID satu byte: tidak dapat membawa ID 548 atau 10500. Gunakan Codec 8E dengan ID dua byte/variable IO atau ALELS JSON; framing tidak diubah. [Codec resmi](https://wiki.teltonika-gps.com/view/Codec).

## C. Matching dinamis

Aktif registry tenant yang sama saja yang dapat diberi nama/type. Kandidat dari MAC eksplisit, asosiasi canonical terverifikasi, atau metadata capture yang disahkan. Namespace+Instance dan UUID+Major+Minor tidak dipotong menjadi MAC. Konflik tetap AMBIGUOUS. Lihat [kontrak konfigurasi](BLE_PRODUCTION_CAPTURE_CONFIGURATION.md).

## D. EyeBeacon

Sensor-only flags ditolak. Battery satu byte hanya memiliki arti bila profil offset/field telah ditetapkan; low-battery tidak dibuat tanpa flags. ID custom enam byte tidak otomatis physical MAC. [Referensi Beacon](https://wiki.teltonika-gps.com/view/Configuring_EYE_beacons).

## E. EyeSensor

Decoder bersama mendukung full manufacturer AD 9A08/version01, actual flags, serta capture pendek eksplisit. Layout pendek sembilan byte: temperature signed BE/100, humidity, movement MSB/state dan 15-bit counter, pitch signed, roll signed BE, battery 2000+raw*10. Sembilan byte tidak membawa Flags; B3 bukan bytes yang diterima. Error field tidak menghapus nilai lain yang valid. [Referensi Sensor](https://wiki.teltonika-gps.com/view/BTSMP1).

## F. Identitas dan keamanan

Tidak ada sample receiver/ID fallback pada parser, monitoring resolver, atau helper display MAC. Overload public helper lama tetap kompatibel tetapi hanya memformat MAC eksplisit. Guard historical Wasted di repository registry tidak diubah karena area tersebut dilindungi instruksi; guard itu bukan resolver monitoring baru. RAW dan unknown parameters tetap tersedia untuk diagnostik.

## G. Data card

| Data | Sumber |
|---|---|
| Name/type | Registry aktif yang terverifikasi |
| MAC | Field MAC atau asosiasi terverifikasi |
| Namespace/Instance, UUID/Major/Minor | Payload/advertisement tervalidasi |
| RSSI | Signed received byte |
| Sensor fields | Profile/flags yang tervalidasi |
| AVL ID, timestamps, protocol | Packet yang sama |
| Device GPS | GPS receiver; bukan posisi beacon |

UI hanya mendapat conditional identity rows; layout, tema, ekspansi, navigasi dan polling tidak diubah. RAW bukan baris utama. Tidak ada calculated location.

## H. RSSI dan distance

RSSI dBm dipertahankan. Estimator scalar Generic existing tetap digunakan (-59 dBm, n=2); hasil estimasi bukan pengukuran lokasi geografis. Ranging 0 m tidak digunakan sebagai kalibrasi 1 m.

## I. Bukti payload aktual

Pembacaan PostgreSQL terakhir menggunakan BEGIN READ ONLY: latest telemetry ID 9390, 262 records dengan IO548, 874 dengan IO10500. Registry asset_ble_sensors ada; ble_beacons dan ble_observations belum ada. Jadi RAW payload terbukti durable di telemetry, tetapi tidak ada bukti persistence terpisah decoded observation pada instalasi ini.

RAW548 record9390:

`010E0001D401067CD9F408DA2C02016E160001C301062A57812B005502090C6A4908A401FFFE67160001D201067CD9F4134EE102090C6C4F12B700FFFF6F`

Parser aktual menghasilkan tiga blok tanpa error:

| ID | RSSI | Additional Data |
|---|---:|---|
| 7CD9F408DA2C | -44 | 6E |
| 2A57812B0055 | -61 | 0C6A4908A401FFFE67 |
| 7CD9F4134EE1 | -46 | 0C6C4F12B700FFFF6F |

Evaluasi decoder dengan profil EYE_SENSOR_SHORT_V1 eksplisit memberi:

| Fixture | Temp °C | RH % | Movement | Counter | Pitch | Roll | mV |
|---|---:|---:|---|---:|---:|---:|---:|
| 0C614C089100FFFE67 | 31.69 | 76 | false | 2193 | 0 | -2 | 3030 |
| 0C615212AF00FFFE6F | 31.69 | 82 | false | 4783 | 0 | -2 | 3110 |
| 0C6A4908A401FFFE67 | 31.78 | 73 | false | 2212 | 1 | -2 | 3030 |
| 0C6C4F12B700FFFF6F | 31.80 | 79 | false | 4791 | 0 | -1 | 3110 |

Ini bukti aritmetika decoder, bukan pengesahan konfigurasi receiver. Register memuat SENSOR2 MAC 7C:D9:F4:14:7A:FC, SENSOR1 MAC 7C:D9:F4:13:4E:E1 dan dua EyeBeacon. MAC-only registry tidak menyimpan captured-ID mapping. Mapping fisik SENSOR1 dan offset/size masih menunggu verifikasi pengguna. Profil sembilan byte tetap PENDING_CONFIGURATION_VERIFICATION sampai bukti tersedia, sesuai instruksi sebelumnya. Battery 6E juga tidak diinterpretasi tanpa profil yang terbukti.

## J. File production yang berubah

- `backend/src/main/resources/application.properties` — Empty-default properties metadata asosiasi dan capture.
- `backend/src/main/java/com/alels/backend/telemetry/ble/repository/BleObservationRepository.java` — Read-only lookup asosiasi verified yang terikat registry tenant aktif.
- `backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java` — Dynamic resolver, explicit profiles, partial fields, protocol/model guards.
- `backend/src/main/java/com/alels/backend/assetregister/service/BleSensorIdentity.java` — Hapus fallback sample; pertahankan public compatibility overload.
- `gateway/src/main/java/com/alels/gateway/parser/EyeBeaconParser.java` — Validasi framing, diagnostics, shared Teltonika decoder dan advertisement metadata.
- `web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx` — Tampilkan identity fields yang benar-benar tersedia pada card existing.
- `web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/types/eyebeacon.ts` — Tipe advertisement tambahan yang nullable.
- `web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-presentation.ts` — Presentasi identity tervalidasi dan protokol.

## K. Cleanup yang diminta

18 source test/fixture BLE-only dihapus setelah verifikasi; lima mixed test tetap ada dengan hanya metode BLE-only dihapus. Manifest [CSV](BLE_FINALIZATION_DELETED_FILES.csv) merekam path, ukuran dan SHA256 source yang tersedia serta generated artifacts. Generated cache/report test lama juga dibersihkan. Runtime parser/classifier, migration, telemetry/history database dan shared infrastructure tidak dihapus.

Mixed test files/methods:

- `gateway/src/test/java/com/alels/gateway/parser/TeltonikaCodec8EParserTest.java`: extractsAndNormalizesBothWifiBeaconIoIdsFromCodec8E, carriesSensorListVariableIoWithTheSamePayloadAsAlelsJson, variableFrame
- `gateway/src/test/java/com/alels/gateway/identity/CanonicalIdentityV1CompatibilityTest.java`: ci08MultipleSimpleAndAdvancedBlocksRemainSeparateDuringNormalization
- `gateway/src/test/java/com/alels/gateway/publisher/DirectDatabaseWifiRegressionPostgresTest.java`: enabledHistoryWritesAllSixObservationsWithTheActualFunctionDefinition, enabledHistoryFailureRollsBackTelemetryAndCannotCompleteTheAckBoundary, installBleFixture
- `ingestion-service/src/test/java/com/alels/ingestion/repository/WifiHistoryRolloutPostgresTest.java`: enabledHistoryFailureRollsBackOffsetsAndTelemetrySoRetryIsNotLost, explicitlyEnabledHistoryStoresObservationAndRemainsIdempotentForKafkaReplay
- `ingestion-service/src/test/java/com/alels/ingestion/outcome/service/TelemetryRecordDecoderTest.java`: retainsDecodedBeaconListOnItsOwnTelemetryMessage, acceptsPayloadBoundedBeaconCountAboveFormerLimit

## L. Kompatibilitas dan proteksi

Snapshot SHA256 mencakup 474 production files; tepat delapan file whitelist di atas berubah dan 466 tetap sama. TCP/ACK/framing, JSON transport, Kafka/DirectDB writer, normalization, retry/idempotency, registry CRUD/Wasted, migration dan firmware tidak diubah dalam finalization. Shared test dependencies dan parser classifier POM tetap diperlukan.

## M. Hasil sebelum cleanup

| Suite | Passed | Fail/error/skip |
|---|---:|---|
| Backend | 100 | 0/0/0 |
| Gateway | 78 | 0/0/0 |
| Ingestion | 18 | 0/0/0 |
| Frontend Node | 23 | 0/0/0 |
| Chrome fixture scenarios | 16 | 0 |
| API fixture → card render | 1 | 0 |

Termasuk isolated PostgreSQL tests dan Codec8E/ALELS equivalent payload fixtures. Build/typecheck frontend lulus, dengan existing chunk-size warning. Disposable PostgreSQL berhenti dan dihapus; database aplikasi tidak ditulis.

## N. Hasil setelah cleanup

Backend final mvn package: 46 tests, nol failure/error/skip, BUILD SUCCESS 19:46:33 WIB. Current XML gateway mencatat 49 tests, nol failure/error, tiga skipped PostgreSQL tests pada run tanpa test database; run isolated sebelumnya tercatat 49 tanpa skip. Ingestion current XML: 14 tests, nol failure/error/skip. Frontend build/typecheck setelah cleanup berhasil; node --test kini nol tests, bukan bukti coverage. Temporary legacy-boundary checker dan final actual548/9-byte decoder checker berhasil lalu dihapus.

Penghapusan BLE-only tests secara eksplisit mengurangi perlindungan regresi otomatis di masa depan; hasil sebelum cleanup adalah bukti saat ini, bukan suite yang masih dapat dijalankan ulang dari source terhapus.

## O. Runtime

Snapshot sebelumnya: health8080 dan frontend5173 HTTP200, received-beacons tanpa session HTTP403. Backend build terbaru tidak dinyatakan sudah dijalankan pada proses aplikasi. Authenticated API/card aktual belum diverifikasi. Tidak membuat JWT, secret, launcher atau restart service ingestion. Data aktual terus tersimpan berdasarkan query read-only terakhir.

## P. Persistensi

Received cards membaca io_data durable dan melakukan decoding saat baca. Tidak menulis hasil enrichment ke database pada request GET. Existing history writer tidak diperluas dalam scope terlindungi ini. Tidak membuat/menerapkan migration atau mengisi asosiasi tebakan agar card terlihat cocok.

## Q. Blocker

- Bukti ID Source serta offset/size capture aktual belum lengkap.
- Mapping fisik SENSOR1 sebelumnya dinyatakan belum verified; register MAC saja bukan bukti.
- Optional identity/history tables belum tersedia; registry tidak mempunyai Namespace/Instance/UUID.
- Deployment build baru dan authenticated API → card pada aplikasi aktual belum terbukti.
- Firmware/model/preset yang belum terdokumentasi tetap unsupported/pending dengan RAW diagnostik.

## R. Status akhir

PARTIAL. Source correction, fixture decoding dan build backend terakhir PASS; raw548 persistence PASS. Dynamic registry matching dan full card path telah diuji pada verified fixtures. Live matching, capture verification, authenticated actual-card rendering dan separate decoded-history persistence belum dapat diklaim PASS. Tidak memulai phase lain.

## Klarifikasi lanjutan: dictionary dan identitas

Pemeriksaan source dictionary aktual: `gateway/src/main/resources/device-dictionary/fmc650.json` dan `alels_hubio.json` sama-sama mendefinisikan 385 sebagai Manual CAN 15, 10500 BLE Advertisement data (Simple), dan 548 Advanced BLE Advertisement data. `fmc150.json` mendefinisikan 385 sebagai Beacon: definisi tidak boleh dipindahkan antar model. DictionaryRegistryResolver existing memilih kontrak fmc650 untuk ALELS Hub; bukan alasan mengubah makna AVL secara global. Monitoring guard menahan385 untuk model Manual CAN yang dikenal dan memerlukan attestation untuk model lain. Generic normalizer masih mengenali385 lintas model; tidak diperluas atau diubah pada pemeriksaan ini karena shared ingestion dilindungi. Validasi runtime dictionary aktif dalam database belum dibuktikan ulang pada langkah ini.

Pengguna menegaskan MAC dan Instance ID berbeda. Ini tidak memberikan bukti baru ID Source, offset/size atau mapping fisik SENSOR1. Karena itu keduanya tetap field identitas terpisah:10500 dapat memberikan Namespace+Instance;548 parameter01 adalah captured Beacon ID, belum tentu MAC ataupun Instance tanpa konfigurasi. Registry dicocokkan hanya lewat MAC/asosiasi verified; registry membership bukan bukti capture profile.
