# Discount Service — Petrol Pump Loyalty

Spring Boot API + PWA. Bill photos are read with **Gemini**. Claims queue until SiteOmat PDF match.

## Local (dev / H2)

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 22)
export GEMINI_API_KEY=your_key_here
./mvnw spring-boot:run
```

Open http://localhost:8080  
Health: http://localhost:8080/api/health

Dev OTP: `123456`  
Admin `9999999999` · Employee `8888888888`  
Redeem QR: `/redeem.html?token=pump-demo-token`

## Live (Docker + Postgres)

1. Copy env file and set Gemini key:

```bash
cp .env.example .env
# edit .env → GEMINI_API_KEY=...
```

2. Start:

```bash
docker compose --env-file .env up --build -d
```

3. Open http://SERVER_IP:8080

4. Set real pump GPS (admin login → or API):

```bash
curl -X PUT http://SERVER_IP:8080/api/admin/pump/geo \
  -H "X-Session-Token: $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"lat":13.74,"lng":76.90,"radiusMeters":50}'
```

5. Print pump redeem page QR pointing to:

`http://SERVER_IP:8080/redeem.html?token=pump-demo-token`

### Production checklist
- [ ] `GEMINI_API_KEY` set
- [ ] Pump lat/lng correct
- [ ] Put HTTPS in front (Caddy/Nginx) + domain
- [ ] Replace `OTP_DEV_CODE` with real SMS OTP provider
- [ ] Change default DB password
- [ ] Rotate `pump-demo-token` via DB/admin later

## Flow
1. Driver registers, adds vehicle  
2. At pump: Upload bill (camera) → Gemini OCR → queued  
3. Admin uploads daily SiteOmat PDF → coins credited  
4. Driver scans pump QR → redeems coins for fuel  
5. Employee sees live redeem feed (view only)
