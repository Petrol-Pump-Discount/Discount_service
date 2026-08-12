# NAGA SHREE Pump Loyalty

Real-world petrol pump loyalty for drivers: camera bill upload → Gemini OCR → daily SiteOmat PDF match → coins → redeem at pump QR.

## Stack

| Layer | Tech |
| --- | --- |
| Frontend | React + Vite PWA (`frontend/`) |
| Backend | Spring Boot 4 + JPA (`backend/`) |
| DB | Postgres 16 (prod) / H2 (local API) |
| OCR | Google Gemini |
| CI/CD | GitHub Actions → GHCR images |

## Product rules (locked)

- Phone OTP accounts; wallet on phone; phone↔vehicle many-to-many
- Landing: Register/Sign in **or** Upload bill (no login)
- Upload: camera only + GPS ≤ 50m of pump
- Claims queue until PDF match (no instant coins)
- 1 coin = 1 paisa; redeem only as fuel via pump merchant QR
- Business day 6am→6am IST
- Roles: DRIVER / EMPLOYEE (live feed view-only) / ADMIN

## Local development

### Backend API

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17 || /usr/libexec/java_home -v 22)
export GEMINI_API_KEY=your_key
./mvnw spring-boot:run
```

API: http://localhost:8080/api/health

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

App: http://localhost:5173 (proxies `/api` → `:8080`)

Dev OTP: `123456`  
Admin `9999999999` · Employee `8888888888`

## Docker (full stack)

```bash
cp .env.example .env   # set GEMINI_API_KEY
docker compose --env-file .env up --build -d
```

- App UI: http://SERVER_IP/  
- API direct: http://SERVER_IP:8080/api/health  
- Pump QR URL: `http://SERVER_IP/redeem?token=pump-demo-token`

### Production pull (after CD)

```bash
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

Images:
- `ghcr.io/petrol-pump-discount/discount_service/backend:latest`
- `ghcr.io/petrol-pump-discount/discount_service/frontend:latest`

## CI/CD

- **CI** (`.github/workflows/ci.yml`): on PR/push to `main` — Maven verify + frontend build
- **CD** (`.github/workflows/cd.yml`): on push to `main` — build/push Docker images to GHCR

## Git workflow

`main` is protected (PR required). Solo flow:

```bash
git checkout -b feat/my-change
# ...
git push -u origin HEAD
gh pr create --base main --fill
gh pr merge --merge
```

## Production checklist

- [ ] `GEMINI_API_KEY` in secrets / `.env` (never commit)
- [ ] Correct pump lat/lng / radius
- [ ] HTTPS (Caddy/Nginx) + domain in front of port 80
- [ ] Replace `OTP_DEV_CODE` with real SMS OTP
- [ ] Strong `DATABASE_PASSWORD`
- [ ] Rotate pump redeem token from `pump-demo-token`
