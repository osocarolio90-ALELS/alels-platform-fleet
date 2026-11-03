# Penyesuaian MAC, jarak, dan urutan kolom BLE

Tanggal: 3 November 2026. Ketiga perubahan sudah diterapkan pada source dan build, lalu diuji. Aktivasi pada aplikasi live memerlukan reload backend dan refresh browser; tidak ada klaim bahwa proses lama sudah memuat build ini.

## MAC EyeBeacon dari Instance ID

Khusus jenis EyeBeacon, MAC yang ditampilkan mengikuti Instance ID dengan format `AA:BB:CC:DD:EE:FF`. Pada IO548 Custom yang membawa captured ID sebagai ID pada card, nilai itu menjadi sumbernya jika Instance ID terpisah tidak tersedia. Contoh: `7CD9F408DA2F` menjadi `7C:D9:F4:08:DA:2F`.

Aturan berada pada helper MAC existing dan dipakai oleh API/enrichment maupun formatter log. Frontend memakai kebijakan yang sama agar card dan tabel BLE konsisten, termasuk saat membaca respons/snapshot lama. EyeBeacon tetap mengikuti Instance ID ketika metadata MAC lama berbeda. Jika tidak ada ID 48-bit yang valid, fallback MAC terverifikasi existing dipertahankan; tidak dibentuk alamat dari UUID atau string yang bukan MAC.

EyeSensor tidak mengikuti aturan tersebut. ID `2A57812B0055` tetap memakai MAC terverifikasi `7C:D9:F4:14:7A:FC`; ketika MAC EyeSensor tidak tersedia, Instance ID tidak dijadikan MAC. Penanganan konflik MAC EyeSensor juga tetap seperti sebelumnya.

Ini adalah kebijakan nilai MAC untuk tampilan/envelope decoded sesuai instruksi pengguna, bukan perubahan byte yang diterima. RAW HEX, captured identity dan field MAC asli pada payload tidak ditulis ulang. Penanda konfigurasi projection diperbarui agar snapshot decoded dapat disegarkan worker setelah backend memuat build baru.

## Keterangan model jarak

Card dan log tidak lagi menampilkan `GENERIC_UNCALIBRATED`, `Generic / Uncalibrated`, atau keterangan Model untuk nilai jarak yang valid. Nilai estimasi, tanda `~`, satuan meter dan rumus existing tidak diubah. Metadata model tetap tersedia secara internal. Alasan kegagalan seperti RSSI tidak tersedia tetap dapat tampil ketika jarak memang tidak dapat dihitung.

## Kolom BLE paling akhir

Urutan parameter dinamis sekarang mempertahankan urutan relatif seluruh parameter telemetry umum, kemudian menempatkan semua `ble.parsed.*` di paling akhir. Satu helper dipakai oleh tabel dan export agar hasil keduanya konsisten.

Tes mencakup baris BLE terbaru yang muncul lebih dahulu, sementara baris lebih lama baru memperkenalkan kolom Engine RPM/Fuel Level. Hasil tetap HDOP, parameter telemetry umum, lalu kolom EyeBeacon/EyeSensor. Kedua log memakai TripLogTable yang sama sehingga berlaku pada Trip & Route dan Log & Message.

## Hasil pengujian

- Backend `mvn package`: 46 tes, 0 failure/error/skip, BUILD SUCCESS.
- Frontend `npm run build`: TypeScript dan Vite lulus; warning chunk existing tidak menjadi kegagalan.
- Verifikasi API/read-model pada data PostgreSQL nyata: MAC EyeBeacon mengikuti ID, MAC EyeSensor tetap, log menampilkan MAC baru tanpa label model jarak. Pemeriksaan hanya membaca database.
- Metadata konflik: EyeBeacon mengikuti kebijakan Instance ID; EyeSensor tetap menolak pasangan MAC yang ambigu.
- Render komponen card nyata: tidak ada keterangan model jarak, angka jarak tetap tampil, EyeSensor tanpa MAC tidak diberi alamat dari Instance ID.
- Render tabel dengan baris sparse: seluruh kolom telemetry umum mendahului kolom BLE. Export memakai helper urutan yang sama.
- RAW, source timestamps, isolasi receiver/tenant, TCP/ACK, ingestion, firmware, migration dan styling umum tidak diubah.

## File source yang berubah

1. `D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/service/BleMacAddress.java`
2. `D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java`
3. `D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/device/deviceworkspace/tab/triproute/repository/DeviceWorkspaceTripRouteRepository.java`
4. `D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-presentation.ts`
5. `D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx` — label tabel MAC disesuaikan menjadi MAC Address.
6. `D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/trip-route/components/trip-log-table.tsx`

Kode verifikasi sementara dibersihkan setelah pengujian; log hasil dipertahankan. Tidak ditambahkan dependency, migration atau suite BLE-only permanen. Tidak dilakukan restart service maupun perubahan database pada tugas ini.
