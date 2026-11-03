> Pembaruan wajib: [verifikasi profil capture EYE Sensor](BLE_SENSOR_CAPTURE_PROFILE_VERIFICATION.md) memperketat gate ke receiver pengujian, identitas terverifikasi dan registrasi EyeSensor aktif. Instruksi konfigurasi profil lama di bawah harus dibaca bersama pembatas terbaru; produksi tetap pending.

# Perbaikan decoding kartu BLE dan jarak RSSI

Status: **PARTIAL**. Implementasi dan tes otomatis selesai. Aktivasi profil IO 548 produksi tetap **PENDING CONFIGURATION VERIFICATION** sesuai konfirmasi pengguna. Backend aktif belum direstart dan respons BLE aplikasi terautentikasi belum divalidasi langsung.

## A-B. Akar masalah dan alur data

Tidak ada estimator jarak sebelumnya. Helper frontend selalu mengembalikan em dash walaupun RSSI tersedia. Referensi RSSI 1 m, eksponen path loss, serta identitas model belum dikonfigurasi. RAW HEX menjadi field utama karena kartu sebelumnya sengaja memperlihatkan data trace; namespace/instance sebenarnya sudah ada pada identity API tetapi belum menjadi field kartu tersendiri. Additional Data IO 548 disimpan pada otherParameters[2] tanpa interpretasi profil capture.

Alur tetap satu: IO 10500/548 → EyeBeaconParser → EyeBeaconTelemetryNormalizer → penyimpanan telemetry/raw/riwayat yang sudah ada → BleObservationService → API kartu → panel bersama. Beacon record, normalizer, persistence dan deduplication tidak diubah. Decoder tambahan hanya dipanggil di pemetaan baca, menggunakan metode baru dalam parser yang sudah ada. Tidak ada parser atau ingestion pipeline kedua.

## C. Model jarak

Estimator terpusat backend: BleRssiDistanceEstimator. Konfigurasi memakai application.properties dan environment Spring yang sudah digunakan aplikasi:

| Environment | Default |
| --- | --- |
| ALELS_BLE_DISTANCE_REFERENCE_RSSI_AT_1M | -59 |
| ALELS_BLE_DISTANCE_PATH_LOSS_EXPONENT | 2.0 |
| ALELS_BLE_DISTANCE_MODEL | GENERIC_UNCALIBRATED |

Properti Spring: alels.ble.distance.reference-rssi-at-1m, alels.ble.distance.path-loss-exponent, alels.ble.distance.model.

Rumus: 10^((referenceRssiAt1m - measuredRssi)/(10*pathLossExponent)). RSSI -44 menghasilkan 0.177827941 m, ditampilkan ~0.18 m dan Model: Generic / Uncalibrated (approximate). Ini estimasi matematis, bukan jarak fisik yang terverifikasi atau kalibrasi Teltonika.

RSSI harus -127..-1; referensi harus finite dan negatif pada rentang RSSI; eksponen harus finite dan positif. Model harus GENERIC_UNCALIBRATED atau CALIBRATED. Hasil non-finite/underflow tidak ditampilkan sebagai angka. Tidak ada cap jarak, arah, koordinat geografis atau penyimpanan posisi. Konfigurasi CALIBRATED dengan referensi/eksponen eksplisit diuji melalui Spring ApplicationContextRunner. Metode estimate(rssi, Model) menyediakan satu formula yang dapat dipakai model per-device terverifikasi di masa depan; pemilihan model per-device belum dibuat.

## D. Decoding IO 10500

Sumber resmi: [FMC650 Beacon List](https://wiki.teltonika-gps.com/view/FMC650_Beacon_List) menjelaskan IO 10500 mode simple dan IO 548 mode advanced; [panduan parsing beacon Teltonika](https://wiki.teltonika-gps.com/view/How_to_start_with_FMB_devices_and_Beacons%3F) menetapkan flag, identitas, RSSI signed, dan field baterai opsional.

Block 03000000000000000000007CD9F408DA22D40C26 memiliki 20 byte; header data-part 11 berada sebelum block, sehingga IO satu block berukuran 21 byte.

| Offset block | Byte | Arti |
| --- | --- | --- |
| 0 | 03 | Flag Eddystone dengan RSSI dan baterai |
| 1..10 | 00000000000000000000 | Namespace 10 byte |
| 11..16 | 7CD9F408DA22 | Instance 6 byte |
| 17 | D4 | Signed int8: -44 dBm; bukan TX power |
| 18..19 | 0C26 | Unsigned big-endian: 3110 mV |

Flag 01 tidak memiliki baterai; flag 07 juga membawa raw temperature; flag 21/23/27 membawa identitas iBeacon (UUID/major/minor). Parser existing tetap dipakai. Nilai temperatureRaw simple dipertahankan tanpa menebak scaling firmware menjadi Celsius. Celsius di API baru berasal dari profil sensor yang dipilih eksplisit. TX/ranging tidak ditambahkan karena block contoh tidak membawa field tersebut.

MAC tidak dibuat dari Instance. Pemetaan terverifikasi existing hanya Instance 7CD9F408DA22 + namespace nol + receiver 863719066421737; perilaku ini tidak diubah.

## E. IO 548 Additional Data

[Sumber resmi EYE Sensor BTSMP1](https://wiki.teltonika-gps.com/view/BTSMP1) mendokumentasikan suhu signed /100, humidity %, bit movement dan count, pitch/roll signed, serta baterai 2000 + nilai*10 mV. Mode advanced memiliki offset/size capture yang dapat dikonfigurasi, sehingga panjang sembilan byte saja tidak cukup untuk menentukan profil.

Fixture memiliki constant 01, panjang block 16 hex (=22 byte isi), TLV RSSI 00/01, Beacon ID 01/06, dan Additional Data 02/09. Decoder EYE_SENSOR_SHORT_V1 membaca susunan capture yang dinyatakan pengguna, tanpa menganggapnya format universal.

| Field / offset Additional Data | Sampel 1 | Sampel 2 |
| --- | --- | --- |
| Beacon ID | 2A57812B0055 | 7CD9F4134EE1 |
| RSSI TLV | BC = -68 dBm | BB = -69 dBm |
| Additional Data | 0C614C089100FFFE67 | 0C615212AF00FFFE6F |
| Temperature, 0..1 | 0C61 /100 = 31.69 °C | 31.69 °C |
| Humidity, 2 | 4C = 76% | 52 = 82% |
| Movement/count, 3..4 | 0891: false /2193 | 12AF: false /4783 |
| Pitch, 5 | 00 = 0° | 0° |
| Roll, 6..7 | FFFE = -2° | -2° |
| Battery, 8 | 67: 3030 mV | 6F: 3110 mV |

Magnet tidak tersedia pada capture ini; null dipertahankan. Decoder memeriksa profil eksplisit, HEX, panjang tepat sembilan byte, humidity dan batas angle yang terdokumentasi. Profil gagal tetap mengembalikan RSSI/raw dan error terstruktur pada received API, bukan menggagalkan telemetry lain.

Pengguna mengonfirmasi IMEI uji 863719066421737 dan kedua sampel tetapi belum mengonfirmasi offset/size langsung di Configurator. Oleh karena itu default profil kosong, tidak diaktifkan global atau otomatis untuk receiver ini. Setelah verifikasi, operator dapat mengisi ALELS_BLE_SENSOR_SHORT_PROFILE_IDENTITIES dengan daftar pasangan receiver-IMEI/Beacon-ID, dipisahkan koma. Dua pasangan fixture diuji secara eksplisit dalam tes, bukan diaktifkan pada produksi. Profil tidak menyatakan Beacon ID sebagai MAC dan tidak mengubah registrasi/type.

Database lokal alels_db dibaca dalam transaksi READ ONLY lalu ROLLBACK: hanya Beacon 1 aktif sebagai EyeBeacon; tidak ditemukan paket IO 548 untuk device 4 saat pemeriksaan. Belum ada dasar untuk mengklaim nilai EyeSensor ini sudah muncul pada data lokal produksi.

## F. File yang diubah

Semua path berikut berada di D:/alels-platform-work/:

- gateway/src/main/java/com/alels/gateway/parser/EyeBeaconParser.java — metode decode profil eksplisit dan record SensorData; parse existing tidak diubah.
- gateway/src/test/java/com/alels/gateway/parser/EyeBeaconParserTest.java — fixture 10500, kedua fixture pendek, signed/invalid values dan raw preservation.
- gateway/src/test/java/com/alels/gateway/parser/TeltonikaCodec8EParserTest.java — IO 548 melalui frame 8E dan JSON dibandingkan dengan normalizer yang sama.
- backend/src/main/java/com/alels/backend/telemetry/ble/service/BleRssiDistanceEstimator.java — estimator terpusat baru.
- backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java — enrichment baca untuk jarak/profil, tanpa perubahan query/identity/dedup/history.
- backend/src/main/java/com/alels/backend/telemetry/ble/dto/EyeBeaconDtos.java — field tambahan measurement.distance, sensorData, temperatureCelsius.
- backend/src/main/resources/application.properties — model RSSI eksplisit dan daftar profil opt-in kosong.
- backend/src/test/java/com/alels/backend/telemetry/ble/service/BleRssiDistanceEstimatorTest.java — formula, invalid input, overflow, override konfigurasi.
- backend/src/test/java/com/alels/backend/telemetry/ble/service/BleReceivedCardsTest.java — read mapping, profil terbatas, source parity, raw/GPS retention dan invalid-profile errors.
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/types/eyebeacon.ts — tipe DTO tambahan.
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-presentation.ts — format estimate dari backend, label model, tanpa formula frontend kedua.
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx — decoded fields, namespace/instance, penghapusan baris raw.
- web-react/tests/ble-sensor-ui.test.mjs — estimasi, label dan invalid-display regression.
- web-react/tests/eyebeacon-ui-smoke.mjs — kontrak decoded fields kedua kartu dan tidak ada baris RAW HEX.
- docs/BLE_CARD_DECODING_DISTANCE_FIX.md — laporan ini.
- docs/BLE_MONITORING_CLEANUP.md — tautan status monitoring terbaru; penghapusan posisi geografis tetap berlaku.

## G-H. API dan kartu sebelum/sesudah

Sebelum: measurement berisi rssiDbm, batteryMillivolts, temperatureRaw, rawBlockHex, lowBattery, otherParameters. Tidak ada estimate; frontend memaksa em dash. Raw dan parameter hex tampil sebagai baris kartu.

Sesudah: field mentah tetap tersedia di API untuk trace internal. measurement.distance menambah meters/model/referenceRssiAt1m/pathLossExponent/reason. Contoh -44 dBm: meters=0.177827941, model=GENERIC_UNCALIBRATED, reference=-59, exponent=2. sensorData menambah nilai decoded hanya jika profil cocok; temperatureCelsius terpisah dari raw. Tidak ada field geographic location.

Kartu menampilkan RSSI dan estimate bersamaan, label model approximate/uncalibrated, namespace/instance untuk Eddystone, serta nilai sensor dengan unit. Baris RAW HEX dan parameter raw tidak dirender di kedua kartu. Diagnostik parser dan data internal tetap tersedia. Tampilan, tab, polling, ekspansi dan GPS receiver dipertahankan. Tidak ada perhitungan jarak frontend kedua.

## I. Hasil regresi

- Gateway mvn install: 69 ditemukan, 64 lulus, 5 dilewati (fixture PostgreSQL khusus), nol failure/error. Parser BLE 16 dan Codec 8E 3 lulus; suite mencakup general telemetry dan hot-path/routing contracts.
- Backend clean package kemudian package ulang setelah koreksi terakhir: 27 tes terarah lulus, nol failure/error/skip. Mencakup estimator, received cards, observation service, register service/controller dan identity.
- Frontend Node: 20 lulus. npm run build/TypeScript lulus; warning chunk besar existing tetap ada.
- Chrome headless: 8 skenario lulus, kedua tipe kartu x light/dark x desktop/mobile. Nilai 31.69 °C, 76%, false/Stationary, count2193, pitch0, roll-2 dan estimate ~0.18 m diperiksa, bersama penghapusan raw/posisi serta kestabilan kartu.
- JAR/backend dan bundle frontend diperiksa: tidak ada kelas/binding geographic positioning yang dihapus sebelumnya.
- Snapshot 234 file dilindungi: satu perubahan yang diizinkan, EyeBeaconParser.java; 233 lainnya tetap identik. Metode parse/normalizer/persistence tidak diubah; penambahan parser hanya fungsi opt-in untuk read mapping.

Codec 8E IO 548 dan JSON IO 548 menghasilkan hasil parsing yang sama; IO 10500 JSON/8E memakai parser existing dan tes Q=68. Tidak diklaim bahwa IO 10500 memiliki humidity/motion jika field itu tidak ada pada payload. Tidak diklaim high AVL ID native Codec 8.

## J-K. Perlindungan arsitektur

Tidak ada perubahan pada TCP, ACK, framing, protokol JSON, firmware, Kafka/Direct Database routing, general ingestion/persistence, lineage/deduplication, BLE Register/Wasted, navigasi, global CSS atau migrasi 026/027. Tidak ada SQL tulis atau tabel baru. Geographic Location Optimization tidak dibuat ulang; estimator hanya menghasilkan scalar meter tanpa GPS, arah, geometri atau confidence lokasi.

## L. Runtime dan pekerjaan tertunda

Vite aktif diperiksa lewat HTTP 200 pada komponen aktual: namespace dan binding sensor terbaru tersedia; primary RAW HEX row tidak ada. Backend PID19568 dimulai 22:04:06 WIB, sebelum estimator baru dikompilasi 22:14:22 WIB. Backend aktif belum direstart dan belum ada verifikasi respons BLE terautentikasi dari proses baru. Hasil build/fixture tidak disamakan dengan validasi runtime itu.

Profil pendek produksi tetap PENDING CONFIGURATION VERIFICATION. Sesudah offset/size serta pasangan identitas dikonfirmasi, aktifkan konfigurasi profil yang tepat; jangan menyimpulkan MAC dari ID. Registrasi EyeSensor dan pencocokan identitas harus valid agar kartu masuk tab EyeSensor. Dedicated PostgreSQL/Kafka replay serta transaksi Register/Wasted produksi tidak dilakukan. Tidak ada upaya mengubah data agar tes tampak lulus.
