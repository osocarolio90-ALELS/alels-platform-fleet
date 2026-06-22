# ALELS 1M Foundation - Organization Structure Fix

Tanggal patch: 2026-06-20

## Standar folder final

Organization dan ServerOps adalah domain/menu berbeda.

Struktur backend yang benar:

```text
backend/src/main/java/com/alels/backend/organization
backend/src/main/java/com/alels/backend/serverops
```

Struktur yang salah dan sudah dihapus:

```text
backend/src/main/java/com/alels/backend/serverops/organization
```

## File Organization final

```text
backend/src/main/java/com/alels/backend/organization/controller/OrganizationController.java
backend/src/main/java/com/alels/backend/organization/dto/OrganizationDtos.java
backend/src/main/java/com/alels/backend/organization/repository/OrganizationRepository.java
backend/src/main/java/com/alels/backend/organization/service/OrganizationService.java
```

## Perbaikan stabilitas backend

1. Package Java Organization distandarkan ke `com.alels.backend.organization`.
2. Import antar class Organization sudah mengikuti package baru.
3. Query PostgreSQL duplicate check tidak lagi menggunakan pola `(? IS NULL OR id <> ?)`.
4. `companyNameExists()` dan `userEmailExists()` dipisahkan menjadi dua query aman:
   - query tanpa excludeId
   - query dengan excludeId
5. Ini mencegah error PostgreSQL `bad SQL grammar` akibat parameter NULL tanpa tipe.

## Perbaikan auth frontend

1. `web-react/src/lib/api.ts` tetap menggunakan Axios central client.
2. Token dibaca dari Zustand auth store dan fallback browser storage.
3. Token juga dapat dibaca dari key session standar `alels-web-react-auth`.
4. Response interceptor hanya logout pada `401 Unauthorized`, bukan `403 Forbidden`.
5. Ini mencegah session hilang saat user valid terkena RBAC forbidden.

## Validasi manual yang wajib dijalankan di Windows project folder

Backend:

```powershell
cd D:\alels-platform-work\backend
mvn clean package -DskipTests
java -jar .\target\alels-backend-1.0.0.jar
```

Frontend:

```powershell
cd D:\alels-platform-work\web-react
npm install
npm run build
npm run dev
```

API check setelah login:

```text
GET /api/organization/companies -> 200 OK
GET /api/organization/company/check-name?name=VIAR -> 200 OK
```

## Catatan environment sandbox

Patch ini dibuat pada container Linux yang tidak memiliki Maven (`mvn: command not found`). TypeScript compile berhasil sampai tahap `tsc -b`, tetapi `vite build` di sandbox berhenti karena optional dependency Rollup Linux tidak tersedia pada node_modules hasil upload Windows. Jalankan `npm install` di Windows sebelum `npm run build`.
