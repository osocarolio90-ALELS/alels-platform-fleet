# ALELS Organization Menu Foundation

Scope: Organization domain menu for ALELS 1M Foundation Mode.

## Submenu Order
1. Company List
2. User List
3. Company Status
4. User Status
5. Wasted

## Rules Implemented
- `ALELS TECH INDONESIA` is enforced as the official root company by migration `005_organization_foundation.sql`.
- Companies created by internal roles (`SUPERADMIN`, `ADMIN`) are placed under `ALELS TECH INDONESIA`.
- Internal ALELS users under the root company do not require `started_at` and `expired_at`.
- Standard roles: `SUPERADMIN`, `ADMIN`, `OWNER`, `MANAGER`, `TECHUSER`, `CLIENTUSER`.
- Reusable DataTable is used for search, show/hide filters, show/hide columns, page size, and pagination.

## Backend Endpoints
- `GET /api/companies`
- `POST /api/company-register`
- `PUT /api/companies/{companyId}`
- `DELETE /api/companies/{companyId}`
- `GET /api/companies/wasted`
- `POST /api/companies/{companyId}/restore`
- `DELETE /api/companies/{companyId}/permanent`
- `GET /api/company-status`
- `POST /api/company-status/{companyId}/activate`
- `POST /api/company-status/{companyId}/suspend`
- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{userId}`
- `DELETE /api/users/{userId}`
- `GET /api/users/wasted`
- `POST /api/users/{userId}/restore`
- `DELETE /api/users/{userId}/permanent`
- `GET /api/user-status`
- `POST /api/user-status/{userId}/activate`
- `POST /api/user-status/{userId}/suspend`
- `GET /api/organization/wasted`

## Validation
Run:

```powershell
cd D:\alels-platform-work\database
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U alels -d alels_db -f .\migrations\005_organization_foundation.sql

cd D:\alels-platform-work\backend
mvn clean package -DskipTests

cd D:\alels-platform-work\web-react
npm install
npm run build
```
