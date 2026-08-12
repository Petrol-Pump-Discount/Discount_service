# Discount Service — Petrol Pump Loyalty

Spring Boot 4 API + static PWA for single-pump fuel loyalty.

## Quick start
```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 22)   # or 17+
./mvnw spring-boot:run
```
Open http://localhost:8080

### Seed accounts (OTP always `123456` in dev)
- Admin: `9999999999`
- Employee: `8888888888` (view-only redeem feed)

### Pump redeem QR
http://localhost:8080/redeem.html?token=pump-demo-token

## Notes
- Bill OCR is stubbed: upload form sends FCC/Trans ID + volume (Vision can replace later).
- PDF match extracts 9-digit Receipt Nos from uploaded SiteOmat PDF/text.
- Business day = 6am–6am IST.
- Geofence uses seeded pump lat/lng (update via `PUT /api/admin/pump/geo`).
