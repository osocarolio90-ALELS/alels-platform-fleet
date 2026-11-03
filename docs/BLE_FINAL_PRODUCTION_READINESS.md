# Final Teltonika BLE protocol & readiness — 2 November 2026

**Keputusan: PARTIAL. SOURCE READY dan TEST READY untuk format/profil yang disebut di bawah; belum RUNTIME VERIFIED, DEVICE VERIFIED atau PRODUCTION VERIFIED.**

## A–D. Referensi, matriks dan firmware

[Matriks kompatibilitas yang dicatat sebelum implementasi](BLE_FIRMWARE_COMPATIBILITY_MATRIX.md) memuat AVL, URL resmi, model/family, firmware, konfigurasi, encoding, isi dan coverage awal. Tidak ada klaim universal dari dokumentasi family lain.

Temuan utama untuk receiver 863719066421737:

- Model database: FMC650. Versi tracker, firmware EYE Sensor dan firmware EYE Beacon terpasang: **UNKNOWN**. Tidak ditemukan metadata firmware/version di kolom devices/device_models, key versi pada telemetry, atau respons versi FMC650 yang tersimpan. Versi bridge/ESP32 pada catatan lama tidak dianggap versi FMC650.
- [Referensi AVL FMC650](https://wiki.teltonika-gps.com/view/FMC650_Teltonika_Data_Sending_Parameters_ID) mencantumkan 10500/548 sebagai BLE variable HEX sampai 1024 byte, tetapi 385 sebagai Manual CAN 15. Read view sekarang menghindari salah tafsir 385 pada model FMC650; raw/general telemetry tidak diubah.
- 11317 dan dedicated 10800–10843 tidak ditemukan pada referensi FMC650 yang diperiksa. Implementasi baru dibatasi model yang memiliki referensi eksplisit GH5200/TMT250/TFT100 dan konfigurasi profil terverifikasi. Bukan pernyataan bahwa FMC650 tidak akan pernah mendukungnya; bukti target/firmware saat ini belum cukup.
- [FMC650 Beacon List](https://wiki.teltonika-gps.com/view/FMC650_Beacon_List) membedakan legacy 548 dan layout parameterized dari 03.01.00.Rev.03. Batas 3.27.07.Rev.00 pada [Advanced Capturing](https://wiki.teltonika-gps.com/view/Advanced_Beacon_Capturing_Configuration) tidak diterapkan otomatis antar-family.
- [BTSMP1](https://wiki.teltonika-gps.com/view/BTSMP1) mencatat filtering by name dan perbedaan endian beberapa field pada revisi tracker .203/.204. Profil eksplisit dipakai, bukan menebak endian dari nilai yang terlihat masuk akal. Catatan EYE firmware 01.02.10+ adalah konfigurasi perangkat BTS, bukan versi FMC650.
- [FMC650 Firmware Changelog](https://wiki.teltonika-gps.com/view/FMC650_Firmware_Changelog) dan [PCN](https://wiki.teltonika-gps.com/view/FMC650_Product_Change_Notifications) ditinjau. Rilis yang didokumentasikan tidak membuktikan versi receiver yang terpasang. Tidak ada upgrade firmware.

## B. Type BLE dan source format

| Registrasi terverifikasi | Source | Isi yang dapat ditampilkan / syarat |
| --- | --- | --- |
| EyeBeacon atau EyeSensor | 10500 Simple | UID/UUID, RSSI, baterai/field yang benar-benar terkirim. Advertisement sensor tidak otomatis memiliki humidity/movement. |
| EyeBeacon atau EyeSensor | 385 Simple family pendukung | Parser legacy tetap tersedia. Tidak dipakai sebagai BLE pada FMC650 yang dikenal. |
| EyeBeacon atau EyeSensor | 548 Advanced | Captured ID/RSSI/Additional Data. Full Eddystone AD dikenali secara struktural; short EYE hanya setelah receiver/identity/profile/type gate. |
| EyeSensor | 11317 | Model/firmware profile dan MAC parameter15 yang cocok registrasi aktif diperlukan. Name-only tidak dijadikan MAC atau identity permanen. |
| EyeSensor | Dedicated IO | Slot-to-MAC terverifikasi dan profil model/firmware diperlukan. Hanya field dari packet yang sama digabung. |
| Unknown/unregistered | Semua | Raw/API observations dipertahankan; tidak dipaksa masuk tab EyeBeacon/EyeSensor. |

Data From, Source Codec, AVL ID, Capturing Mode, advertising protocol dan Type BLE adalah konsep terpisah. Registrasi tetap authoritative untuk type. ALELS JSON tidak membuktikan codec asal.

## E–F. Decoder dan integrasi yang dikerjakan

Satu EyeBeaconParser tetap dipakai. Tidak ada parser/service ingestion kedua.

### Simple / Advanced existing

385/10500 Eddystone dan iBeacon, optional battery/raw temperature, legacy/new548 dan 10828/10829/10831 dipertahankan. Shared TLV boundary reader dipakai oleh format 548 dan 11317. IO10500 optional temperatureRaw tetap disimpan tanpa menebak scaling firmware menjadi Celsius. Field sensor yang belum terverifikasi tidak diisi dengan tebakan.

Full Eddystone Additional Data dikenali hanya sebagai AD service-data dengan UUID/frame/length/RFU yang sah. Namespace/Instance dan ranging data dapat dibaca. [Spesifikasi UID Google](https://github.com/google/eddystone/blob/master/eddystone-uid/README.md) mendefinisikan ranging pada 0 m; nilai ini tidak menjadi reference RSSI 1 m pada estimator. Contoh RSSI -79 dengan ranging +2 tetap menghasilkan generic distance 10 m, bukan hasil kalibrasi dari +2.

### Sensor List 11317

Decoder memeriksa constant01, panjang tiap sensor dan TLV, known field widths, duplicates, HEX dan batas 1024 byte. Parameter name/MAC/sensor, unknown TLV dan raw block dipertahankan. Record malformed dengan boundary yang masih jelas menghasilkan structured error tanpa membuang record valid berikutnya. Record tanpa MAC tetap unresolved; nama tidak digunakan sebagai identity.

Profil EYE_LIST_V203_LE menukar byte temperature/roll saja sesuai catatan contoh; counter/battery tidak dibalik secara global. EYE_LIST_V204_BE menggunakan big-endian temperature/roll. Signed pitch dan roll, Celsius/100, boolean, count dan battery mV diuji. Doc example memiliki ketidakkonsistenan prose: D608 little-endian = 2262 → 22.62°C, F9 signed = -7°, AF00 little-endian roll = 175°. Tes mengikuti byte/type, bukan menyalin angka prose yang bertentangan.

### Dedicated IO

Whitelist tepat (slot1–4): 10800–03 temperature; 10804–07 humidity; 10808–11 magnet; 10812–15 movement; 10816–19 pitch; 10820–23 low battery; 10824–27 battery; 10832–35 roll; 10836–39 movement count; 10840–43 magnet count. **10828–31 bukan rentang dedicated yang diisi otomatis.**

[Referensi GH5200](https://wiki.teltonika-gps.com/view/GH5200_Bluetooth%C2%AE_4.0_settings) dan [AVL GH5200](https://wiki.teltonika-gps.com/view/GH5200_AVL_ID_List) menjadi dasar family/slot/error handling, bukan asumsi FMC650. Nilai no-data/sensor-error menghasilkan null + fieldErrors, bukan suhu/gerakan palsu. Magnet counter memerlukan dukungan firmware EYE evaluasi terkait; tidak diklaim universal.

Numeric IO dibaca dari telemetry.io_data, bukan nilai hasil multiplier di normalized table. Audit general parser menunjukkan numeric wire IO disimpan sebagai integer, numeric policy mempertahankan integer, dan NormalizationEngine menyimpan hasil multiplier terpisah. Tidak ada perubahan pada fungsi general tersebut. Input numeric yang tidak sesuai tipe/range tidak dibuat menjadi measurement.

### Konfigurasi dan batas arsitektur

Default konfigurasi baru kosong:

- ALELS_BLE_SENSOR_AVL_PROFILES: receiver-IMEI=EYE_LIST_V203_LE atau EYE_LIST_V204_BE, setelah model/firmware dipastikan.
- ALELS_BLE_SENSOR_SLOT_IDENTITIES: receiver-IMEI/slot=MAC yang dibuktikan, tanpa generated MAC. Mapping ganda ambigu tidak digabung.
- Profil short9 receiver863719066421737/verified identity/active EyeSensor dari pekerjaan sebelumnya tetap dipertahankan, bukan diaktifkan global.

Representasi baru terhubung pada **read view raw telemetry**; general ingestion dan normalized BLE-history writer tidak diubah. Raw telemetry mempertahankan IO, tetapi dukungan normalized-history khusus untuk representasi baru memerlukan review terpisah sebelum diklaim. Tidak ada migrasi/tabel baru atau bypass protected write contracts.

## G. Provenance di kedua kartu

Kedua kartu menampilkan Data From, Source Codec, AVL ID / IO ID, BLE Capturing Mode, Protocol BLE dan Type BLE. JSON menampilkan codec Not Provided / Unknown. Native protocol yang tercatat menampilkan Codec8 atau Codec8 Extended yang sesuai. Physical transport UNKNOWN tidak diubah menjadi klaim WiFi/cellular.

Source.ioId lama dipertahankan sebagai primary ID; source.ioIds menyimpan semua contributing IDs dari satu packet. Dedicated measurement menyimpan sourceIoIds tiap field. Kartu menampilkan IO dan waktu Device/Server pada field multi-ID sehingga nilai lama tidak dilabeli milik packet baru. Event IDs yang tidak membuktikan capture mode menampilkan Unknown.

Tidak ada penggabungan measurement lintas packet/slot tanpa provenance. Packet dedicated tanpa RSSI tidak memakai RSSI lama; distance tetap unavailable. Registered name berbeda dari advertised name; keduanya tidak menjadi implicit identity alias. Receiver GPS tetap metadata source, tanpa Calculated BLE Latitude/Longitude/Altitude.

## H. Output fixture

| Format / fixture | Hasil aktual |
| --- | --- |
| 385 dan10500 iBeacon | UUID00112233445566778899AABBCCDDEEFF, major1/minor2, RSSI-44, battery3110; flag27 raw temp21 dipertahankan |
| 10500 Beacon1 block | Namespace nol, Instance7CD9F408DA22, RSSI-44, battery3110 mV |
| 548 short1 | 31.69°C,76%,false,count2193,pitch0,roll-2,battery3030 mV |
| 548 short2 | 31.69°C,82%,false,count4783,pitch0,roll-2,battery3110 mV |
| 11317 official two-name example, V203 | Dua record tetap terpisah, MAC tidak dikarang; typed sample22.62/22.51°C dan boundary/error tests lulus |
| 11317 V204 with explicit MAC fixture | Dua registered synthetic sensors,31.69°C,humidity76/82,source11317; RSSI/distance null |
| Dedicated two-slot fixture | Dua identity terpisah,humidity76/82; sourceIDs masing-masing slot; RSSI/distance null |
| Actual local PostgreSQL READ ONLY | Delapan parsed identities dari existing10500 packets; Beacon1 -44 dBm/3110mV. Ini database-read test, bukan respons HTTP live. |

Bukti MAC untuk positive API/card fixtures bersifat sintetis dan hanya ada dalam tes. File DTO fixture berada di backend/target/ble-profile-api-fixture.json, ble-sensor-list-api-fixture.json dan ble-dedicated-api-fixture.json. Raw values tetap internal/API; tidak menjadi baris utama kartu.

## I. Test matrix aktual

| No. requirement | Verifikasi yang dijalankan | Hasil |
| --- | --- | --- |
| 1–4 | Simple385/10500 Eddystone dan iBeacon termasuk optional fields | PASS fixtures |
| 5–8 | Legacy/new548, full Eddystone AD, short9 scoped profile | PASS fixtures; actual capture config pending |
| 9–10 | Multi-sensor11317, TLV boundaries/IDs/lengths/unknowns/endian | PASS format profiles; bukan FMC650 device claim |
| 11–12 | Exact dedicated whitelist, two slots, signed/error values, missing identity | PASS conditional model/slot fixtures |
| 13–14 | Registration mapping/type, unknown/deleted/wrong profile | PASS |
| 15 | Actual Codec8E frame extraction548/10500/11317 + JSON consistency | PASS fixtures |
| 16 | Existing Codec8/general gateway tests | PASS unchanged suite; high AVL IDs not claimed native Codec8 |
| 17 | ALELS JSON10500/548 normalizer tests | PASS |
| 18–19 | Exact source IDs and codec on EyeBeacon/EyeSensor,11317/dedicated cards | PASS headless fixtures |
| 20–21 | Scaling/battery, signed RSSI and centralized generic distance | PASS |
| 22 | New dedicated packet never inherits old RSSI/distance | PASS Node regression |
| 23–25 | Multi-device, wrong receiver/model/profile, malformed records | PASS tested cases |
| 26 | Controller fixture548 → card; DTO fixtures11317/dedicated → card | PASS controlled tests, not live HTTP |
| 27 | Device/company access tests + PostgreSQL negative-scope queries | PASS |
| 28 | Register save/update/move/delete/restore and card names on isolated PostgreSQL | 12 PASS; production implementation unchanged |
| 29 | Gateway/ingestion regression + protected-file hash audit | PASS executed suites; live routing replay not performed |
| 30 | Compiled JAR/frontend audit; no geographic BLE engine/bindings | PASS |

Actual totals: backend **45 passed**, including12 isolated Register PostgreSQL tests and2 local READ ONLY tests. Gateway **76 discovered:71 passed,5 skipped** (dedicated DirectDatabase fixture unavailable). Ingestion **15 passed**. Frontend Node **23 passed**. Chrome **16 scenarios passed** (four representations × light/dark desktop/mobile), including pending transitions and per-measurement IO/time labels.

Backend clean package succeeded after an initial failed compile attempt; no workaround was added to shared/auth code. Final clean log: D:/alels-platform-work/backend/target/ble-final-clean-build.log. Frontend build/TypeScript passed; existing large-chunk warning remains. Test database port55444 was stopped and its workspace directory removed. No persistent fixture data was written to the application database.

## J. Runtime / safe restart audit

Read-only search, including hidden/ignored startup filenames, found deploy/docker-compose.yml and deploy/ha/docker-compose.capacity-lab.yml. Neither defines a backend service. Main Compose has postgres,redis,redpanda,console/topic-init only. No backend launcher script or matching Windows backend service was found. Docker CLI was not found in PATH or its standard installation path. Root/deploy .env files were absent. Current execution shell had no JWT/database/root-bootstrap launch environment.

Health checks actually attempted:

- http://localhost:8080/actuator/health → connection refused.
- http://localhost:5173 → connection refused.

Per user instruction, no new launcher, environment values, JWT or application credentials were created. No application service was started/stopped/restarted. Existing manual Maven instructions require a launch environment that is unavailable here; generating a replacement JWT would violate the instruction. Safe restart/rollback cannot be marked verified without the existing launcher/environment and known deployed artifact. Pengguna telah mengonfirmasi bahwa path launcher/service dan terminal environment belum dapat dipastikan. **Runtime Verification: BLOCKED — Backend Environment Unavailable.** Tidak ada launcher, JWT, credential, atau konfigurasi pengganti yang dibuat. Verifikasi API HTTP terautentikasi dan tab pada aplikasi aktual tetap belum dapat dilakukan; pengujian fixture/API MockMvc/card di atas tidak menggantikannya.

Fixture/MockMvc and database read checks do not constitute authenticated live API or live-tab verification. Those checks remain blocked. No DEVICE VERIFIED or PRODUCTION VERIFIED claim is made.

## K. Exact modified/deleted files

Paths relative to D:/alels-platform-work/:

- gateway/src/main/java/com/alels/gateway/parser/EyeBeaconParser.java — shared TLV,11317, dedicated numeric/slot decoder and full Eddystone AD metadata in existing parser.
- gateway/src/test/java/com/alels/gateway/parser/EyeBeaconParserTest.java
- gateway/src/test/java/com/alels/gateway/parser/TeltonikaCodec8EParserTest.java
- backend/src/main/java/com/alels/backend/telemetry/ble/repository/BleObservationRepository.java — raw candidate IDs and tracker model metadata; company/device/cursor scope retained.
- backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java — conditional read mapping, FMC650385 guard, source IDs and no stale fusion.
- backend/src/main/java/com/alels/backend/telemetry/ble/dto/EyeBeaconDtos.java — additive source/measurement/profile metadata.
- backend/src/main/resources/application.properties — empty opt-in profile/slot settings.
- backend/src/test/java/com/alels/backend/telemetry/ble/service/BleReceivedCardsTest.java
- backend/src/test/java/com/alels/backend/assetregister/service/BleSensorRegisterPostgresTest.java — isolated schema fixture gains model metadata required by read query; production Register is untouched.
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/types/eyebeacon.ts
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-presentation.ts
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx
- web-react/tests/ble-sensor-ui.test.mjs
- web-react/tests/eyebeacon-cards.test.mjs
- web-react/tests/eyebeacon-ui-smoke.mjs
- docs/BLE_FIRMWARE_COMPATIBILITY_MATRIX.md
- docs/BLE_FINAL_PRODUCTION_READINESS.md
- docs/BLE_SENSOR_CAPTURE_PROFILE_VERIFICATION.md — link to this updated readiness report.

No source files were deleted in this task. No Git stage/commit/reset occurred. The repository already contains earlier uncommitted work; a HEAD-wide diff includes that work and is not a task-only diff.

## L. Stable architecture confirmation

A234-file protected snapshot had one permitted production change: EyeBeaconParser.java. The other233 files, including transport/ACK/framing, general normalization/persistence, Kafka/Direct DB routing, firmware, migrations026/027, Register/Wasted production and tab navigation, remained identical. No global CSS/theme or unrelated workspace modules changed. Read-side decoders do not create a second ingestion pipeline or alter source-event identity. Scalar RSSI distance remains Generic/Uncalibrated unless explicitly configured otherwise. No geographic Location Optimization was reintroduced.

## M. Unsupported configurations / blockers

1. Installed firmware and actual field-selection/preset/slot proof for target FMC650 are still UNKNOWN/pending.
2. 11317/dedicated108xx applicability is not claimed for FMC650; conditional decoder tests are for documented profiles/families.
3. Name-only11317 cannot be matched permanently to a registry MAC without additional verified identity evidence.
4. Full EYE manufacturer scan-response decoding outside the verified short profile is not added speculatively. Full raw data is preserved; full Eddystone UID AD recognition is separately supported.
5. FMC650 custom sensor IO701..744 and older preset-specific sensor IO remain general telemetry until scaling, slot and identity configuration are verified. They are not auto-labelled EYE from numeric IDs.
6. Dedicated IO only combines one packet; it does not retain old humidity/RSSI under a newer timestamp. Cross-packet state fusion and normalized BLE-history expansion require separate approved contracts.
7. Actual application startup/authenticated API/tabs and physical field traffic remain unverified. The missing existing launcher/environment cannot be replaced under current user restrictions.

## N. Final readiness decision

| Level | Decision |
| --- | --- |
| SOURCE READY | YES, for implemented and explicitly documented conditional profiles; not universal firmware compatibility |
| TEST READY | YES, executed fixtures/integration/builds above; skipped tests identified |
| RUNTIME VERIFIED | BLOCKED — Backend Environment Unavailable; frontend aktual juga tidak tersedia |
| DEVICE VERIFIED | NO — actual firmware/configuration and fresh target traffic not verified |
| PRODUCTION VERIFIED | NO |

The system has stronger format/provenance handling and regression protection for field testing, but must not be advertised as production verified until the existing runtime is started safely and actual FMC650 traffic/configuration are checked.
