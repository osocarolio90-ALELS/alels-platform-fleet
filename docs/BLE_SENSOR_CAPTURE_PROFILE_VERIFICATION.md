> Pembaruan 2 November: [final protocol dan production readiness](BLE_FINAL_PRODUCTION_READINESS.md) menambahkan matriks firmware, provenance kartu,11317/dedicated read profiles dan hasil runtime terbaru. Pembatas short9 tetap berlaku.

# Verifikasi profil capture EYE Sensor — receiver 863719066421737

Status akhir: **PARTIAL / PENDING CONFIGURATION VERIFICATION untuk aktivasi produksi**. Audit, penguatan pembatas, controller API fixture dan tampilan kartu selesai diuji. Kedua identitas sampel belum diaktifkan pada konfigurasi produksi dan belum dianggap sebagai MAC hanya karena memiliki enam byte.

## Temuan dokumentasi resmi

- [EYE Sensor BTSMP1](https://wiki.teltonika-gps.com/view/BTSMP1): movement memakai bit tertinggi dari word, count memakai 15 bit sisanya; suhu /100, humidity persen, pitch dan roll signed, serta baterai 2000 + nilai*10 mV.
- [EYE Beacon BTSID1](https://wiki.teltonika-gps.com/view/EYE_BEACON_/_BTSID1): kemampuan flag BTSID1 terbatas pada low battery/battery. Data sensor BTSMP1 tidak boleh otomatis diterapkan kepada EyeBeacon.
- [FMC650 Beacon List](https://wiki.teltonika-gps.com/view/FMC650_Beacon_List) dan [Advanced Beacon Capturing Configuration](https://wiki.teltonika-gps.com/view/Advanced_Beacon_Capturing_Configuration): Additional Data merupakan bagian advertisement yang dipilih lewat offset/size. Beacon ID Source dapat berupa bagian ID advertisement atau MAC; ID enam byte saja tidak membuktikan MAC.

BTSMP1 dan Advanced Capturing dapat dibaca langsung. Halaman FMC650/BTSID1 mengembalikan 403 pada direct fetch; isi relevannya diperiksa melalui hasil indeks resmi Teltonika. Audit tidak memakai dokumen pihak ketiga untuk menetapkan layout.

## Layout yang diverifikasi pada fixture

| Offset capture | Panjang | Konversi profil EYE_SENSOR_SHORT_V1 |
| --- | --- | --- |
| 0..1 | 2 byte | Temperature signed big-endian /100 °C |
| 2 | 1 byte | Humidity unsigned % |
| 3..4 | 2 byte | Movement = (word & 0x8000) != 0; counter = word & 0x7FFF |
| 5 | 1 byte | Pitch signed ° |
| 6..7 | 2 byte | Roll signed big-endian ° |
| 8 | 1 byte | Battery = 2000 + unsigned byte*10 mV |

Susunan ini hanya berlaku setelah field selection dan capture profile dibuktikan. Capture sembilan byte tidak mengandung Flags. B3 (bits 7,5,4,1,0) konsisten dengan kumpulan field tersebut, tetapi **tidak dinyatakan diterima**. Low-battery dan magnet tidak dibuat dari Flags yang hilang. API tidak menambahkan field flags. Tes menolak B3 yang disisipkan sebelum sembilan byte, dan membuktikan 8891 berarti movement=true/count2193 sedangkan 0891 berarti false/count2193.

## Bukti lokal yang tersedia

Pencarian workspace tidak menemukan ekspor konfigurasi FMC650/preset capture yang relevan. Database lokal dibaca menggunakan transaksi READ ONLY lalu ROLLBACK:

- Device 4 / IMEI 863719066421737: tidak ada telemetry dengan IO 548.
- raw_packets: 6.819 paket ALELS_JSON, nol kandidat tekstual key 548 pada payload/raw_json; data terakhir 29 Oktober 2026 16:42:03 WIB.
- Registrasi aktif company terkait hanya Beacon 1, MAC 7C:D9:F4:08:DA:22, tipe EyeBeacon.

Karena tidak ada full advertisement yang cocok dengan kedua sampel maupun bukti offset/size/Beacon ID Source dari Configurator, field selection dan relasi identitas produksi belum dapat dibuktikan. Kesamaan nilai fixture tidak menggantikan bukti tersebut. Status produksi tetap pending, bukan decode otomatis.

## Penguatan implementasi

Logika konversi sembilan byte pada EyeBeaconParser yang sudah ada sesuai fixture dan tidak diduplikasi. Perubahan dibatasi read mapping dan isi kartu:

1. Hard gate receiver tepat 863719066421737 dan IO 548.
2. Profil capture dipilih eksplisit per pasangan receiver/Beacon ID; default konfigurasi tetap kosong.
3. Harus ada pencocokan identitas MAC yang terverifikasi dengan registrasi aktif bertipe EyeSensor.
4. Jika captured ID bukan MAC yang dibuktikan independen, konfigurasi profil dapat memuat pasangan receiver/Beacon-ID=verified-MAC. Ini deklarasi bukti oleh operator setelah verifikasi, bukan konversi ID→MAC otomatis. Mapping kosong/invalid/konflik tidak membuat identitas valid. MAC berbeda dari bukti identitas yang sudah ada juga tidak diterima untuk decode.
5. Registrasi EyeBeacon, deleted/unregistered, receiver lain, ID lain atau profil tanpa bukti menghasilkan null sensorData dan status pending/unsupported; RSSI, jarak dan raw tetap dipertahankan.
6. API menambah measurement.sensorCapture: status DECODED, PENDING_CONFIGURATION_VERIFICATION, INVALID_PROFILE_DATA atau UNSUPPORTED_RECEIVER beserta alasan. Payload tidak cocok tetap tersimpan sebagai raw; error profil invalid ditelusuri lewat telemetry/IO/byte offset.
7. Kartu EyeSensor menampilkan penjelasan pending tanpa mempertahankan nilai sensor lama saat respons terbaru belum terverifikasi. EyeBeacon tidak mendapat pengukuran sensor dari profil ini. RSSI dan Estimated Distance tetap terpisah.

Konfigurasi ALELS_BLE_SENSOR_SHORT_PROFILE_IDENTITIES tidak diisi untuk produksi. Syarat aktivasi berikutnya: bukti Manufacturer ID/offset/size, Beacon ID Source/offset/size, Additional Data offset/size, serta hubungan identitas terverifikasi ke registrasi EyeSensor. Tidak ada perubahan BLE Register atau migrasi.

## Hasil end-to-end fixture: parser → service → controller API → kartu

Tes menggunakan parser/service/controller produksi, repository/device/registrasi fixture tanpa database mutation. Identitas MAC 02:00:00:00:00:01/02 hanya bukti **sintetis khusus tes**, bukan MAC produksi kedua Beacon ID. Konfigurasi fixture menegaskan profil dan mapping secara eksplisit.

MockMvc memanggil GET /api/ble/gateways/33/received-beacons dengan principal fixture, memeriksa status HTTP200 dan field JSON, lalu menulis respons ke D:/alels-platform-work/backend/target/ble-profile-api-fixture.json. Chrome headless memakai JSON respons ini secara langsung pada EyeBeaconPanel (assetType=EyeSensor) / BeaconCard yang sama dengan aplikasi. Ini integrasi terkontrol, bukan uji hardware atau backend live terautentikasi.

| Field API dan kartu | Sampel 1 | Sampel 2 |
| --- | --- | --- |
| Beacon ID | 2A57812B0055 | 7CD9F4134EE1 |
| Capture | 0C614C089100FFFE67 | 0C615212AF00FFFE6F |
| sensorCapture.status | DECODED (fixture tervalidasi) | DECODED (fixture tervalidasi) |
| Temperature | 31.69 °C | 31.69 °C |
| Humidity | 76% | 82% |
| Movement | false / Stationary | false / Stationary |
| Movement counter | 2193 | 4783 |
| Pitch / roll | 0° / -2° | 0° / -2° |
| Battery | 3030 mV | 3110 mV |
| RSSI | -68 dBm | -69 dBm |
| Estimated Distance | ~2.82 m | ~3.16 m |
| Model | Generic / Uncalibrated | Generic / Uncalibrated |
| Magnet | unavailable | unavailable |

Jarak hanya estimasi matematis model -59 dBm/n=2, bukan jarak fisik terverifikasi. rawBlockHex dan otherParameters[2] tetap ada dalam respons internal, tetapi tidak menjadi baris pengukuran kartu. Receiver GPS tetap berasal dari observasi sumber. Tidak ada Flags B3 atau geographic position yang ditambahkan.

## Regresi dan build aktual

- Backend: 28 tes terarah lulus; nol gagal/error/skip. Mencakup controller API, kedua source representation, mapping identitas eksplisit, registered type, deleted/unknown, receiver lain, profil invalid, pending, raw/RSSI/GPS retention dan distance.
- Gateway: 70 ditemukan; 65 lulus, 5 dilewati karena fixture PostgreSQL khusus tidak dijalankan; nol gagal/error. Parser test movement MSB/Flags ditambah tanpa perubahan parser produksi/framing.
- Frontend Node: 20 lulus.
- Chrome headless: delapan skenario lulus, kedua jenis kartu x light/dark x desktop/mobile. EyeSensor memakai respons controller fixture. Kedua humidity/counter, suhu, movement, pitch, roll, distance dan absennya RAW/posisi diuji. Transisi menjadi pending menghapus nilai sensor dan baterai yang tidak lagi tersedia.
- Backend package dan frontend build/TypeScript lulus; warning chunk besar frontend existing tetap ada.
- Snapshot 234 file produksi yang dilindungi tetap identik: gateway/ingestion, firmware, migrasi, BLE Register, navigasi. Database hanya dibaca.
- JAR backend dan bundle frontend diperiksa: tidak ada geographic Location Optimization Engine, calculated-coordinate binding atau status geometri yang dihidupkan kembali.

## File yang diubah pada pekerjaan ini

- D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java
- D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/dto/EyeBeaconDtos.java
- D:/alels-platform-work/backend/src/main/resources/application.properties
- D:/alels-platform-work/backend/src/test/java/com/alels/backend/telemetry/ble/service/BleReceivedCardsTest.java
- D:/alels-platform-work/gateway/src/test/java/com/alels/gateway/parser/EyeBeaconParserTest.java
- D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/types/eyebeacon.ts
- D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx
- D:/alels-platform-work/web-react/tests/eyebeacon-ui-smoke.mjs
- D:/alels-platform-work/docs/BLE_SENSOR_CAPTURE_PROFILE_VERIFICATION.md
- D:/alels-platform-work/docs/BLE_CARD_DECODING_DISTANCE_FIX.md (tautan pembaruan pembatas)

## Runtime dan keterbatasan

Vite aktif merespons HTTP200 dan menyajikan Sensor Data Availability terbaru tanpa primary RAW HEX row. Backend aktif PID20640 dimulai 22:23:22 WIB, sebelum build pembatas terbaru sekitar 23:18 WIB. Tidak dilakukan restart backend atau klaim respons live BLE sudah memakai pembatas baru.

Aktivasi kedua identitas produksi tetap pending sampai bukti capture/identitas tersedia. Tidak ada uji perangkat fisik, Kafka/Direct DB replay atau transaksi registrasi produksi. Tidak ada TCP/ACK/framing/JSON/firmware, general telemetry, persistence, registry, migrasi, susunan tab atau global UI yang diubah. Tidak ada geographic location optimization.
