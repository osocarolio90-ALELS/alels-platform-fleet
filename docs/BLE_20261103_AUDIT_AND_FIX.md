# Audit dan perbaikan BLE — 3 November 2026

**Kode, build, database, dan pengujian integrasi: LULUS. Aktivasi pada backend live: menunggu restart.** Pengguna menjawab “Belum direstart”, sehingga laporan ini tidak mengklaim tampilan browser live telah diverifikasi.

Laporan mencatat perubahan pada tugas ini saja. Perubahan lain yang sebelumnya sudah ada di worktree dipertahankan.

## 1. BLE Register dan migration 027

Menu, route, controller, service, repository, serta komponen register/Wasted BLE sudah tidak digunakan. Audit source tidak menemukan dependensi runtime ke `asset_ble_sensors`.

File `database/migrations/027_ble_sensor_register.sql` dihapus. Audit database sebelum penghapusan menemukan **4 data register, 0 foreign key pemakai, dan 0 view pemakai**. Sesuai instruksi eksplisit pengguna, tabel register beserta datanya dihapus menggunakan DROP tanpa CASCADE. Tabel dan history BLE 026 tetap tersedia. Tidak ada migration pengganti atau migration baru; file migration 026 tidak diubah pada tugas ini.

Manifest: [penghapusan 027](D:/alels-platform-work/docs/BLE_20261103_REMOVALS.csv).

## 2. Perangkat baru diproses tanpa daftar ID

Data aktual IO 548 memuat EyeBeacon baru dengan captured ID `7CD9F408DA2F`. Konfigurasi sebelumnya hanya mencantumkan tiga ID lama sehingga record baru belum mendapat klasifikasi.

Resolver sekarang mencari satu profil yang cocok dari daftar profil capture receiver yang sudah dikonfirmasi: `EYE_BEACON_BATTERY_V1|EYE_SENSOR_SHORT_V1`. Metadata deployment menggunakan `863719066421737/*`, sehingga ID baru pada rule capture EYE tersebut tidak memerlukan registrasi atau penambahan ID.

Validasi tetap memakai decoder existing. Profil yang tidak cocok atau ambigu menghasilkan error terstruktur; RAW tetap disimpan. Receiver yang tidak dikonfigurasi tidak mewarisi profil ini. Panjang payload bukan aturan global untuk seluruh BLE, dan prefix MAC tidak digunakan sebagai bukti model hardware.

Penanda konfigurasi disimpan dalam JSON snapshot 026. Ketika metadata berubah, worker memproses ulang projection yang perlu diperbarui, maksimal 25 packet per siklus. Source record key dan timestamp pengukuran tidak berubah. Alias retransmisi tetap dikenali agar tidak membuat blok ganda.

### Sampel aktual yang diverifikasi

Telemetry **9896**, receiver **863719066421737**, sumber **ALELS JSON**, IO **548**. Timestamp device: **3 November 2026, 10:56:36 WIB**; server: **10:56:48.556004 WIB**. GPS receiver: latitude **-6.2111583**, longitude **107.009975**, altitude **32 m**.

| Captured ID | Jenis | RSSI | Battery | Temperature | Humidity | Counter | Pitch / Roll |
|---|---|---:|---:|---:|---:|---:|---|
| 2A57812B0055 | EyeSensor | -73 dBm | 3050 mV | 36,98 °C | 65 %RH | 2226 | 0° / -2° |
| 7CD9F408DA2F | EyeBeacon baru | -58 dBm | 3090 mV | — | — | — | — |
| 7CD9F408DA2C | EyeBeacon | -42 dBm | 3080 mV | — | — | — | — |
| 7CD9F4134EE1 | EyeSensor | -60 dBm | 3120 mV | 36,82 °C | 75 %RH | 4793 | 0° / -1° |

Kedua EyeSensor berstatus Not Moving. Keempat blok dibaca kembali dari 026 sebagai VALID. Angka ini berasal dari sampel 9896, bukan penggantian menggunakan contoh timestamp lama.

MAC terverifikasi yang sudah tersedia tetap dipisahkan dari captured ID. MAC fisik EyeBeacon baru belum dibuktikan oleh payload sehingga tidak diisi dari tebakan. Secara khusus, `2A57812B0055` tetap berelasi dengan MAC fisik `7C:D9:F4:14:7A:FC`.

## 3. Informasi card

Urutan informasi yang diperbesar sekarang:

1. Server Timestamp
2. Device Timestamp
3. Type BLE
4. Beacon ID / Instance ID
5. MAC Address
6. Protocol BLE — hanya pada EyeBeacon
7. Data From
8. AVL ID / IO ID
9. BLE Capturing Mode
10. IMEI Device

Bagian lokasi tetap Latitude, Longitude, Altitude milik receiver. Pengukuran berurutan RSSI, Estimated Distance, Battery Voltage, kemudian khusus EyeSensor: Temperature, Humidity, Movement Status, Movement Counter, Pitch, Roll.

Field Name BLE Sensor, Namespace/UUID, Source Codec, dan field tambahan di luar daftar tersebut tidak lagi ditampilkan pada card ini. Nilai tambahan tetap tersedia pada API/diagnostik. Nilai nol dan false tetap ditampilkan dengan benar. Expand/collapse, styling, warna, urutan tab, serta penjelasan jarak Generic/Uncalibrated dipertahankan. Tidak ada perhitungan lokasi geografis BLE.

## 4. Kolom log dari data 026

Trip & Route dan Log & Message memakai `TripLogTable` serta jalur read-model history yang sama. Jalur itu sekarang membaca snapshot/diagnostik 026 dan mengganti parameter generik IO 548/10500 dengan kolom `BLE <captured ID> (IO <id>)`.

Isi kolom mencakup jenis, MAC terverifikasi jika tersedia, RSSI, estimasi jarak, battery, temperature, humidity, movement/counter, pitch/roll, serta status/diagnostik. RAW HEX tidak menjadi nilai utama. Data yang belum selesai diproyeksikan diberi keterangan parsing pending; blok PARTIAL/ERROR tidak disamarkan menjadi pengukuran valid.

Field API `eyeBeacons` kini memuat blok tersimpan yang sesuai receiver. Endpoint export memakai read-model yang sama sehingga menerima nilai terdecode juga. Tidak dibuat parser atau jalur pemrosesan paralel. Filter dictionary memakai tipe VARCHAR sesuai schema aktual untuk `source_io_id` dan `io_id` pada tabel normalisasi/IO.

Snapshot pemeriksaan database: **4.754 baris blok/diagnostik**, **4.410 snapshot decoded**, source tersimpan terbaru **9943**, dan telemetry IO 548 terbaru **9943**. Data terus masuk setelah penghapusan register. Refresh metadata history yang tersisa akan dilanjutkan worker build baru setelah reload backend; RAW dan history sumber tidak dihapus.

## 5. Isolasi IMEI dan tenant

Join history mensyaratkan IMEI terpilih, device ID pembawa, company/tenant, ID telemetry sumber, dan timestamp server sumber yang sama. Identitas beacon saja tidak menjadi dasar pemilihan history. Source receiver di dalam snapshot juga diperiksa sebelum pengukurannya ditampilkan.

Sebelumnya `placeholderData` dapat mempertahankan baris device lama saat device berganti. Kedua log kini memakai satu guard cache yang hanya mempertahankan pagination untuk device yang sama. Baris TripLogTable juga difilter menurut IMEI terpilih. Tidak ada perubahan izin akses atau route auth lain.

## 6. Hasil pengujian

| Pemeriksaan | Hasil |
|---|---|
| Backend `mvn package` | 46 tes; 0 failure, error, skip; BUILD SUCCESS |
| Frontend `npm run build` | TypeScript dan Vite lulus; warning ukuran chunk existing |
| Empat blok aktual | Dua EyeBeacon dan dua EyeSensor; ID baru terdecode dan tersimpan |
| Replay | Tidak menambah blok sumber ganda |
| Pembacaan history aktual | Empat blok dan empat kolom decoded; kolom BLE Advertisement generik tidak muncul |
| Dua receiver, ID beacon sama | Masing-masing card/history hanya mendapat source dan RSSI miliknya (-80 vs -30) |
| Receiver ketiga / tenant berbeda | Tidak memperoleh data receiver lain |
| Fixture database | TEMP tables dan ROLLBACK; 0 record fixture pada telemetry publik |
| Render React dari hasil backend nyata | Empat card, urutan field tepat, table log berisi nilai decoded |
| Pergantian device pada React Query | Empat skenario lulus: trip log, traffic, packets, summary; cache lama tidak bocor |
| Pagination device sama | Data sebelumnya tetap dapat dipertahankan |
| ID EyeBeacon dan EyeSensor belum dikenal | Otomatis diproses pada receiver terkonfigurasi, dalam kedua urutan blok |
| Receiver tanpa profil | Tidak mewarisi profil receiver lain |
| Capture 2 byte / humidity invalid | Diagnostik jelas; blok dan field valid lainnya tetap tersedia |
| Perubahan metadata | Penanda konfigurasi berubah untuk memicu refresh projection |

Fixture sintetis awal yang mempunyai HEX ganjil ditolak parser sesuai validasi strict. Fixture uji diperbaiki, lalu seluruh pemeriksaan discovery di atas lulus; tidak ada pelonggaran parser produksi.

Source TCP/ACK, Codec framing, format ALELS JSON, firmware, Kafka/DirectDB, persistence telemetry umum, serta source identity tidak diedit pada tugas ini. Sequence per-record Codec 8E existing tetap mencakup timestamp dan record index.

## 7. File source pada perubahan ini

- `D:/alels-platform-work/backend/config/ble-capture.properties`
- `D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java`
- `D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/repository/BleObservationRepository.java`
- `D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/device/deviceworkspace/tab/triproute/repository/DeviceWorkspaceTripRouteRepository.java`
- `D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx`
- `D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/trip-route/components/trip-log-table.tsx`
- `D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/logs-message/pages/logs-message-workspace-tab.tsx`
- Dihapus: `D:/alels-platform-work/database/migrations/027_ble_sensor_register.sql`

File pengujian sementara, class, bundle dan JSON fixture dihapus setelah hasil dicatat. Shared test infrastructure dipertahankan. Sesuai instruksi cleanup sebelumnya, tidak ditambahkan kembali suite BLE-only permanen; pengujian sementara ini adalah bukti verifikasi sesi ini, bukan coverage permanen untuk perubahan masa depan.

## 8. Status aktivasi

Pengguna menyatakan backend **belum direstart** setelah build ini. Restart backend melalui mekanisme dan environment existing, lalu refresh browser untuk memverifikasi empat card serta kolom decoded pada kedua log. Gateway tidak perlu direstart. Tidak dibuat launcher, JWT, credential, atau environment override baru; shell ini tidak mempunyai environment peluncuran backend tersebut.

Tidak ada failure yang tersisa pada pemeriksaan yang telah selesai. Hal ini bukan jaminan matematis zero bug untuk seluruh konfigurasi tracker yang belum diuji. Status tampilan browser dengan sesi login tetap **menunggu reload/verifikasi runtime**.

Manifest cleanup verifikasi: [BLE_20261103_VERIFICATION_CLEANUP.csv](D:/alels-platform-work/docs/BLE_20261103_VERIFICATION_CLEANUP.csv). Log hasil pengujian dipertahankan di folder target masing-masing modul.
