# ESP32 — perbaikan Wi-Fi, JSON, SD, dan ACK ALELS

Firmware: `firmware/V2_9_2_FMC650_IO10500_RECORD_FIX/V2_9_2_FMC650_IO10500_RECORD_FIX.ino`.
Dasar: revisi existing di repository, bukan membuat firmware paralel. Lampiran pengguna adalah revisi lebih lama. Alur tetap UART FMC650 → JSON → SD → TCP ID/ACK → telemetry/ACK → hapus file setelah seluruh record diakui.

## Temuan dari log dan kontrak server

- `"713":h` bukan JSON valid. Login TCP yang mendapat ACK tidak membuktikan telemetry berikutnya berhasil. Nilai `h` tidak boleh diganti 0 atau ditebak.
- Handler ALELS aktif di `NettyDeviceChannelHandler` dan `ClientSession` mengirim ASCII `01\n`; telemetry di-ACK setelah publish berhasil. ACK JSON dari kelas `AlelsJsonAckService` bukan kontrak handler TCP aktif ini. Server tidak diubah.
- Kode lampiran menghabiskan retry file berikutnya walaupun koneksi sudah gagal, dan mengeluarkan file dari antrean RAM setelah max retry. Revisi repository sudah memiliki FIFO, retry transport berkelanjutan, quarantine `.bad`, pemeriksaan write, serta Q yang hanya maju setelah save berhasil. Mekanisme ini dipertahankan.

## Perubahan dalam pekerjaan ini

1. Parser konfigurasi SSID mendukung spasi, `@`, quoted input, serta escaped quote/backslash. Parameter selain SSID tetap mengikuti parser existing. SSID ambigu yang mengandung ` key=value` harus memakai kutip. Batas tetap 1–32 byte.
2. Grammar angka JSON diperketat: tidak menerima `h`, `+1`, `01`, `.5`, `1.`, atau eksponen tidak lengkap. Nilai IO rusak dilaporkan dan tidak dimasukkan; tidak dibuat nilai pengganti. Validator file tersimpan menggunakan grammar yang sama.
3. IO 548/11317 dipertahankan sebagai HEX string seperti 10500; tidak dikonversi menjadi angka. Tidak mengubah decoder backend.
4. Penulisan record SD memeriksa seluruh separator, indent, dan payload dalam satu write, serta Print write-error. Pesan SAVED/counter/Q hanya maju setelah keberhasilan save. Flush tidak merupakan jaminan terhadap listrik mati/kerusakan fisik SD.
5. TCP telemetry harus menulis seluruh payload beserta CRLF. Partial write menutup koneksi dan mempertahankan file.
6. Heartbeat ACK timeout menutup koneksi agar ACK terlambat tidak mengakui record berikutnya. ID/telemetry ACK tetap `01`, dengan satu request yang ditunggu pada satu waktu.
7. File tidak boleh dianggap terkirim jika ada baris non-record yang tidak dikenali. File asli dipertahankan melalui quarantine; separator array standalone tetap didukung.
8. Path pending yang hilang dikeluarkan sebagai MISSING, bukan SENT, hanya setelah direktori SD dapat dibuka. SD tidak dapat dibaca tetap retry. Tidak menghapus file lain.

## Penggunaan

Buka sketch pada folder bernama sama melalui Arduino IDE. Contoh perintah serial setelah membuka akses serial sesuai mekanisme existing:

```text
set ssid="ALELS 4G"
set ssid="ALELS 5G"
set ssid=ALELS@4G
set ssid=ALELS 4G server=192.168.1.9 port=5050
```

Nama `ALELS 5G` dapat disimpan; kemampuan menyambung tetap tergantung band Wi-Fi yang didukung board/access point. Tidak mengubah default server/credential atau membuat credential baru.

## Validasi

- Regression existing gateway: AlelsJsonParserTest 4, NettyProtocolDecoderTest 5, GatewayDurabilityContractTest 3: **12 lulus**, tanpa failure/error/skip. Ini menguji sisi server; bukan eksekusi firmware di ESP32.
- Pemeriksaan tambahan melalui class `AlelsJsonParser` aktual: payload dengan `"713":h` ditolak; payload tanpa field rusak diterima dengan IMEI, Q=25 dan koordinat tetap utuh. Pemeriksaan ini menggunakan Java source launcher dengan class gateway existing, bukan service aktif.
- **Compile ESP32 BERHASIL**, exit 0: FQBN `esp32:esp32:esp32`, core/libraries 3.3.10, default partition. Flash 1.058.144 byte (80% dari 1.310.720); global RAM 52.224 byte (15%), tersedia 275.456 byte untuk alokasi lokal/dinamis. Ini bukan pengukuran peak heap saat Wi-Fi/SD aktif.
- Percobaan compile pertama di sandbox gagal memuat DLL compiler. Build final berhasil memakai toolchain existing di luar sandbox; tidak mengganti compiler atau konfigurasi. Log: `firmware/target/esp32-final-build.log`; image build: `firmware/target/esp32-tcp-fix/V2_9_2_FMC650_IO10500_RECORD_FIX.ino.bin`. Belum di-flash.
- Belum melakukan flash atau pengujian fisik Wi-Fi, SD, listrik mati, dan ACK timeout. Backend aktual belum tersedia; tidak mengklaim upload end-to-end terverifikasi.
- File lama dengan `h` tidak memiliki bukti nilai numerik asli. File `.bad` wajib dipertahankan untuk audit; jangan hapus sebelum diperiksa. Jika satu file berisi record valid dan rusak, quarantine mempertahankan seluruh file, tetapi record yang belum terkirim di file itu memerlukan recovery terpisah.
- Retry parsial dapat mengirim ulang record yang sudah di-ACK sebelumnya, dengan Q asli. Tidak ada checkpoint per-record baru atau perubahan kontrak deduplikasi server.

Tidak mengubah backend produksi, ingestion, TCP server/ACK, Codec, ALELS JSON server, database, BLE Register, UI, maupun konfigurasi runtime aplikasi. Tidak ada upload otomatis ke perangkat.
