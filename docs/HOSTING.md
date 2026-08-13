# Production hosting — finalized stack
#
#   Neon          = PostgreSQL
#   Cloudflare    = Domain + HTTPS (orange-cloud proxy)
#   Hetzner / DO  = VPS running Docker (app + web only)

## Architecture

```text
Phone / browser
    → Cloudflare (DNS + TLS)
    → VPS :80  web (Nginx → React, proxies /api)
    → app :8080 (Spring Boot)
    → Neon Postgres (sslmode=require)
Bill photos → Docker volume `uploads` on the VPS
```

Local dev still uses `docker-compose.yml` (Postgres in Docker).  
Production uses `docker-compose.prod.yml` (no Postgres container).

---

## 1. Neon database

1. Create project at [https://neon.tech](https://neon.tech)
2. Create database (default `neondb` is fine)
3. Connection details → prefer **pooled** host (`…-pooler…`)
4. Build JDBC URL:

```text
jdbc:postgresql://ep-XXXX-pooler.REGION.aws.neon.tech/neondb?sslmode=require
```

5. Put into `.env`:

```bash
DATABASE_URL=jdbc:postgresql://ep-XXXX-pooler.REGION.aws.neon.tech/neondb?sslmode=require
DATABASE_USER=your_neon_user
DATABASE_PASSWORD=your_neon_password
```

Tables are created automatically on first app boot (`ddl-auto: update`).

---

## 2. VPS (Hetzner or DigitalOcean)

1. Create Ubuntu 22.04/24.04 droplet, **2 GB RAM**, public IPv4
2. Firewall: allow **22**, **80**, **443** (443 only if you terminate TLS on the box; with Cloudflare flexible/full to :80, 80 is enough)
3. SSH in and install Docker:

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # re-login after
```

4. Deploy app:

```bash
sudo mkdir -p /opt/pump && sudo chown $USER:$USER /opt/pump
cd /opt/pump
git clone https://github.com/Petrol-Pump-Discount/Discount_service.git .
cp .env.example .env
nano .env   # Neon + Gemini + Twilio + pump lat/lng
```

**First deploy (build on server):**

```bash
./scripts/deploy.sh
```

Or pull GHCR images after CI/CD has published:

```bash
echo YOUR_GITHUB_PAT | docker login ghcr.io -u YOUR_GITHUB_USER --password-stdin
IMAGE_TAG=latest ./scripts/deploy.sh --pull
```

5. Check:

```bash
curl -s http://127.0.0.1/api/health
# open http://SERVER_IP/ in a browser
```

---

## 3. Cloudflare domain + HTTPS

1. Buy/transfer domain into Cloudflare
2. DNS → **A** record `@` → VPS public IP → **Proxied** (orange cloud)
3. Optional: `www` CNAME → `@` (proxied)
4. SSL/TLS mode: **Full** (if origin has HTTPS) or **Flexible** (HTTP to origin :80 — OK for v1)
5. Wait for DNS; open `https://yourdomain.com`

Redeem QR for the pump:

```text
https://yourdomain.com/redeem?token=YOUR_REDEEM_TOKEN
```

Change `pump-demo-token` after go-live (update `pumps` row in Neon).

---

## 4. Secrets checklist (`.env` on VPS)

| Variable | Required |
| --- | --- |
| `DATABASE_URL` / `USER` / `PASSWORD` | Neon |
| `GEMINI_API_KEY` | Bill OCR |
| `OTP_PROVIDER=twilio` + Twilio SIDs | Real SMS OTP |
| `ADMIN_PHONES` / `EMPLOYEE_PHONES` | Your real 10-digit staff mobiles (promoted on boot) |
| `PUMP_LAT` / `PUMP_LNG` / `PUMP_RADIUS_METERS` | Geofence |

Never commit `.env`. Rotate Twilio Auth Token if it was shared in chat.

---

## 5. Failsafe ops

| Concern | What we do |
| --- | --- |
| Process crash | `restart: unless-stopped` on app + web |
| App readiness | Healthchecks; web waits until `/api/health` is OK |
| DB | Neon managed backups / point-in-time (plan-dependent) |
| Bill photos | Docker volume `uploads` on VPS — snapshot VPS or back up `/var/lib/docker/volumes` |
| Deploy | `./scripts/deploy.sh` pulls/rebuilds and recreates containers |
| Rollback | Set `IMAGE_TAG=<previous-sha>` and redeploy |

Useful commands:

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps
docker compose -f docker-compose.prod.yml --env-file .env logs -f app
docker compose -f docker-compose.prod.yml --env-file .env restart app
```

---

## 6. Local vs production

| | Local | Production |
| --- | --- | --- |
| Compose file | `docker-compose.yml` | `docker-compose.prod.yml` |
| DB | Postgres container | Neon |
| UI | http://localhost:3000 | https://yourdomain.com |
| OTP | `console` or Twilio | Twilio on clean network |

```bash
# local full stack
docker compose --env-file .env up --build -d
```
