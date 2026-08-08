# ALELS Emergency Recovery

Folder ini hanya untuk kondisi darurat operasional, bukan workflow harian aplikasi.

Gunakan recovery jika:
- superadmin/root tidak bisa login,
- root company tersuspend/terhapus soft delete,
- semua admin terkunci karena human error,
- bug RBAC/session membuat akses administrative terkunci.

Rule penting:
- Jangan jadikan script ini sebagai endpoint API atau tombol web.
- Jalankan dari server/database console dengan akses terbatas.
- Backup database sebelum recovery.
- Semua script menulis audit ke `organization_activity_logs` dengan action `RECOVERY_*`.

Urutan recovery superadmin root:

```powershell
pg_dump -U alels -d alels_db -f D:\backup_alels_before_recovery.sql
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U alels -d alels_db -f D:\alels-platform-work\database\recovery\002_restore_root_company.sql
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U alels -d alels_db -f D:\alels-platform-work\database\recovery\001_unlock_root_superadmin.sql
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U alels -d alels_db -v "new_password_hash=$hash" -f D:\alels-platform-work\database\recovery\003_reset_root_superadmin_password.sql
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U alels -d alels_db -f D:\alels-platform-work\database\recovery\004_recovery_status_check.sql
```

Script `003_reset_root_superadmin_password.sql` tidak memiliki password atau hash bawaan. Hash BCrypt wajib diberikan melalui variabel `psql` `new_password_hash` pada setiap recovery.

Jika ingin password lain:

```powershell
cd D:\alels-platform-work\backend
mvn dependency:build-classpath "-Dmdep.outputFile=cp.txt"
$cp = Get-Content .\cp.txt -Raw
javac -cp "$cp" .\BcryptTool.java
java -cp ".;$cp" BcryptTool PASSWORD_BARU
```

Jangan menyalin hash ke source repository. Berikan hash hanya saat eksekusi:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U alels -d alels_db -v "new_password_hash=$hash" -f D:\alels-platform-work\database\recovery\003_reset_root_superadmin_password.sql
```
