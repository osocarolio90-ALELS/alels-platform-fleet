# Perbaikan card IO 548 / 10500 — 2 November 2026

## Penyebab yang terverifikasi

Data memang masuk ke `telemetry.io_data`. Sample telemetry **8852**, server **13:54:43 WIB**, memuat tiga blok 548 yang valid:

| Captured ID | RSSI | Additional Data |
| --- | ---: | --- |
| 7CD9F4134EE1 | -56 dBm | 0E664B12B700FFFF70 |
| 7CD9F408DA2C | -47 dBm | 6E |
| 2A57812B0055 | -58 dBm | 0E7A41089E01FFFE69 |

Parser existing menghasilkan ketiganya. Card disaring karena captured ID belum terhubung ke MAC registrasi yang terverifikasi. Banyaknya paket 548 baru juga membuat 10500 historis berada di luar 50 paket pertama. Pagination existing dipertahankan; data lama tersedia melalui Load older BLE packets.

## Perubahan produksi (hanya sisi baca BLE)

- `backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java`: berdasarkan konfirmasi eksplisit pengguna, menambahkan dua alias khusus receiver **863719066421737**. ID **7CD9F408DA2C** → MAC **7C:D9:F4:08:DA:2C**; ID **2A57812B0055** → MAC **7C:D9:F4:14:7A:FC**. Untuk 10500, masing-masing dibatasi Eddystone namespace **00000000000000000000** dan **18B47FACD68055AAC35F**. Untuk 548, dibatasi captured CUSTOM ID yang sama. Alias konfigurasi eksplisit existing tetap memiliki prioritas. Nama/Type BLE tetap berasal dari registrasi aktif dan scope tenant existing. Tidak mengubah identitas/raw MAC parser.
- `backend/src/main/java/com/alels/backend/telemetry/ble/dto/EyeBeaconDtos.java`: mengekspos flag `hasUnresolvedObservations` agar pesan unresolved yang sudah ada di UI dapat muncul. Tidak ada perubahan layout/component/tab/theme.

Mapping **7CD9F4134EE1 → MAC belum diaktifkan**. Registrasi SENSOR 1 saja tidak membuktikan captured ID adalah physical MAC.

Verifikasi identitas **tidak** mengaktifkan decoder Additional Data. Offset/size capture belum terbukti, sehingga **PENDING_CONFIGURATION_VERIFICATION** tetap berlaku. Tidak membuat temperature, humidity, movement, pitch, roll, maupun battery. Byte tunggal `6E` pada EyeBeacon juga tidak otomatis dianggap sebagai nilai baterai. RAW HEX tetap berada pada telemetry/API diagnostic fields; tidak ditambahkan sebagai baris utama card. RSSI dan Generic/Uncalibrated Estimated Distance tetap tersedia.

## Bukti baca database aktual

Transaksi PostgreSQL ditegakkan READ ONLY lalu ROLLBACK; menggunakan kredensial existing, tanpa DDL/DML.

Pengujian ulang menghasilkan:

| Card/ID | Telemetry | MAC terverifikasi | RSSI | Estimated Distance |
| --- | ---: | --- | ---: | ---: |
| BEACON 2 | 8930 | 7C:D9:F4:08:DA:2C | -46 dBm | ~0,22 m |
| SENSOR 2 | 8911 | 7C:D9:F4:14:7A:FC | -59 dBm | ~1,00 m |
| 7CD9F4134EE1, unresolved | 8906 | tidak tersedia | -57 dBm | ~0,79 m |

Ini adalah observasi terbaru masing-masing identitas pada snapshot pengujian, bukan penggabungan measurement antar-paket. ID yang belum terverifikasi tetap ada di respons API beserta RSSI, distance, source IO dan RAW; tidak disamarkan sebagai sensor registered di card. Artifact: `backend/target/ble-current548-readonly.json`.

## Pengujian aktual

- Backend `mvn package`: **BUILD SUCCESS**, 98 test ditemukan, **82 lulus**, 16 dilewati, 0 failure/error. Yang dilewati: 12 isolated Register PostgreSQL dan 4 isolated BLE history repository PostgreSQL karena fixture database khusus tidak diaktifkan. Tidak membuat database fixture untuk pekerjaan ini.
- Termasuk 16 received-card tests (dua regresi baru memakai sample 548 aktual dan mapping 10500), serta 3 test PostgreSQL READ ONLY dengan data aktual.
- Test 10500 historis diperbaiki agar menggunakan cursor source 10500 sendiri; sebelumnya test mengasumsikan 10500 selalu ada dalam 50 paket terbaru, yang tidak lagi benar ketika data 548 baru masuk.
- Frontend existing Node tests: **23 lulus**.
- `node tests/ble-received548-card-fixture.mjs`: **PASS**. Respons API fixture dari sample aktual dirender melalui komponen BeaconCard existing: BEACON 2/EyeBeacon dan SENSOR 2/EyeSensor terpisah, nama/MAC/RSSI/distance/AVL 548 benar, status pending dan unavailable values tetap benar, RAW tidak menjadi baris card. Ini rendering fixture/SSR, bukan pemeriksaan sesi browser pengguna.
- Uji sumber ekuivalen ALELS JSON dan Codec 8E tetap menghasilkan identitas/measurement yang konsisten. Receiver lain, namespace salah, dan MAC belum terverifikasi tidak diaktifkan.
- Snapshot 474 file produksi: hanya dua file BLE read-side di atas berubah. Gateway/parser/normalizer, ingestion, TCP ACK/framing, ALELS JSON transport, firmware, migrations, register dan seluruh source frontend tetap identik pada pekerjaan ini.

Log build: `backend/target/ble548-readonly-build.log`. Fixture terkendali: `backend/target/ble-actual548-api-fixture.json`.

## Runtime dan penerapan

Backend port8080 dan frontend5173 aktif (HTTP200); endpoint BLE tanpa sesi mengembalikan403. Proses backend existing dimulai13:48WIB melalui Maven pada terminal VS Code, sebelum build perbaikan ini selesai14:06WIB. Tidak ada devtools dependency yang dapat diandalkan untuk memuat ulang class. Shell agent tidak memiliki environment peluncur/JWT existing.

**Perbaikan source/build selesai; penerapan pada proses backend aktual dan verifikasi browser terautentikasi masih PENDING.** Tidak menghentikan proses, menyalin environment dari proses lain, membuat JWT/credential/launcher, atau mengubah service. Backend perlu dimuat ulang dari terminal VS Code existing yang sudah memiliki konfigurasi peluncur. Pengujian service READ ONLY/fixture bukan bukti proses lama sudah memakai build baru.

Skill yang ditinjau: [computer-use SKILL.md](C:/Users/Hendri/.codex/plugins/cache/openai-bundled/computer-use/26.1027.30824/skills/computer-use/SKILL.md). Terminal UI tidak diotomasi: [computer-use guidance](C:/Users/Hendri/.codex/plugins/cache/openai-bundled/computer-use/26.1027.30824/docs/guidance.md) melarang "Do not run Windows terminal commands via UI automation directly or indirectly." Restart manual melalui terminal existing mempertahankan environment tanpa membuat konfigurasi baru.

## File test/dokumentasi

- `backend/src/test/java/com/alels/backend/telemetry/ble/service/BleReceivedCardsTest.java`
- `backend/src/test/java/com/alels/backend/telemetry/ble/service/BleReceivedReadOnlyPostgresTest.java`
- `web-react/tests/ble-received548-card-fixture.mjs`
- `docs/BLE_548_10500_RECEIVED_CARD_FIX.md`

Tidak ada commit/staging/reset, migrasi, perubahan data registry/history, atau pengaktifan location engine.
