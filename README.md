ALELS Energy Price Country Link Targeted Patch

Files to replace:
- backend/src/main/java/com/alels/backend/assetregister/controller/EnergyPriceController.java
- backend/src/main/java/com/alels/backend/assetregister/service/EnergyPriceService.java
- backend/src/main/java/com/alels/backend/assetregister/repository/EnergyPriceRepository.java
- web-react/src/features/asset-register/api/energy-price-api.ts
- web-react/src/features/asset-register/pages/energy-price-page.tsx

Purpose:
- Add Country selector to Asset Register > Harga Energy.
- GET /api/asset-register/energy-prices now accepts optional ?countryCode=ID|MY|SG|...
- Harga Energy rows are created per company + energy + country.
- Reference price is refreshed from energy_reference_prices for the selected country.
- If company price is not manually overridden, Price Energy follows Reference Price Country.
- Edit remains limited to SUPERADMIN and ADMIN by existing service role guard.

No migration is required if migration 010 already exists and contains:
- energy_reference_countries
- energy_reference_prices with country_code
- company_energy_prices.country_code

TCP Gateway is not touched.
