# ALELS Server Operations Restart Structure

Scope perubahan ini adalah restart backend/frontend dengan mempertahankan menu **Server Operations** yang sudah fix.
Gateway tidak diubah.

## Backend

Backend sekarang diarahkan menjadi serverops-only backend:

- `backend/src/main/java/com/alels/backend/serverops/auth`
- `backend/src/main/java/com/alels/backend/serverops/health`
- `backend/src/main/java/com/alels/backend/serverops/overview`
- `backend/src/main/java/com/alels/backend/serverops/aiops`
- `backend/src/main/java/com/alels/backend/serverops/gateway`
- `backend/src/main/java/com/alels/backend/serverops/traffic`
- `backend/src/main/java/com/alels/backend/serverops/database`
- `backend/src/main/java/com/alels/backend/serverops/storage`
- `backend/src/main/java/com/alels/backend/serverops/security`
- `backend/src/main/java/com/alels/backend/serverops/shared`

Folder backend lama seperti company, user, asset, domain, security root, config root, dan common root sudah dikeluarkan dari source backend agar trace bug per menu lebih jelas.

## Migration

Migration disederhanakan menjadi:

- `database/migrations/001_serverops_foundation.sql`

Isi migration hanya schema minimum yang dibutuhkan Server Operations:
companies, users, devices, raw_packets, telemetry, unknown_io_registry, ai_ops tables, login/security/audit events.

Default login development setelah migrate:

- Email: `osocarolio90@gmail.com`
- Password: `TEMP_OWNER_PASSWORD`
- Role: `SUPERADMIN`

Catatan production: ganti password seed dan gunakan password hashing sebelum go-live.

## Frontend

Frontend diarahkan menjadi Server Operations focused shell:

- route default `/` dan `/dashboard` diarahkan ke `/server-monitor/overview`
- menu Organization, Asset, dan dashboard lama dikeluarkan dari router/layout
- fitur yang dipertahankan: `auth` dan `server-monitor`

## Validasi di sandbox

- TypeScript check berhasil: `web-react/node_modules/.bin/tsc -b`
- Vite build belum dapat dijalankan di sandbox karena optional dependency Rollup Linux (`@rollup/rollup-linux-x64-gnu`) tidak tersedia dari node_modules bawaan zip Windows. Jalankan `npm install` di environment target lalu `npm run build`.
- Maven compile belum dapat dijalankan di sandbox karena command `mvn` tidak tersedia. Jalankan `mvn -f backend/pom.xml -DskipTests compile` di environment Windows Anda yang memiliki Maven.
