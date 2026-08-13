# Discount Service — Nagashree Pump Loyalty

Drivers earn coins from fuel bills (Gemini OCR → daily SiteOmat PDF match) and redeem at the pump QR.

## Finalized production stack

| Layer | Service |
| --- | --- |
| Database | **Neon** (managed Postgres) |
| Domain + HTTPS | **Cloudflare** |
| App + UI | **Hetzner or DigitalOcean VPS** (Docker) |

Full runbook: **[docs/HOSTING.md](docs/HOSTING.md)**

```text
Cloudflare → VPS :80 (web) → app → Neon
```

## Local development

```bash
cp .env.example .env   # set GEMINI_API_KEY; OTP_PROVIDER=console is fine locally
docker compose --env-file .env up --build -d
```

- UI: http://localhost:3000  
- API: http://localhost:8080/api/health  
- Local DB: Postgres container (not Neon)

```bash
cd frontend && npm ci && npm run dev   # optional; proxies /api → :8080
cd backend && ./mvnw spring-boot:run   # H2 file DB if not using Compose
```

## Production deploy (VPS)

1. Create Neon DB → put JDBC URL + user + password in `.env`  
2. Create VPS → install Docker → clone repo  
3. Fill `.env` (Neon, Gemini, Twilio, pump GPS)  
4. Run:

```bash
chmod +x scripts/deploy.sh
./scripts/deploy.sh
```

5. Point Cloudflare **A** record (proxied) at the VPS IP  

See [docs/HOSTING.md](docs/HOSTING.md).

## Product rules (locked)

- Phone OTP; wallet on phone; phone↔vehicle many-to-many  
- Upload without login; camera only; GPS ≤ 50m  
- Claims queue until PDF match; 1 coin = 1 paisa; redeem fuel only via pump QR  
- Business day 6am→6am IST  

## Roles

| Role | Access |
| --- | --- |
| Driver | Upload, wallet, redeem |
| Employee | Live redeem feed (view only) |
| Admin | PDF, rates, geo, blacklist, claims |

Staff roles live in DB (`app_users.role`). Set via Admin → Staff, or SQL. No env-based role promotion.

## CI/CD

- **CI** — Maven verify + frontend build on PR/`main`  
- **CD** — Push `backend` / `frontend` images to GHCR on `main`  

## Git

`main` is protected. Branch → PR → merge.

```bash
git checkout -b feat/my-change
git push -u origin HEAD
gh pr create --base main --fill
gh pr merge --merge
```
