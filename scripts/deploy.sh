#!/usr/bin/env bash
# Deploy app + web on a VPS (Neon DB via .env).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

COMPOSE=(docker compose -f docker-compose.prod.yml --env-file .env)

if [[ ! -f .env ]]; then
  echo "Missing .env — copy .env.example and fill Neon / Twilio / Gemini values."
  exit 1
fi

required=(DATABASE_URL DATABASE_USER DATABASE_PASSWORD GEMINI_API_KEY)
for k in "${required[@]}"; do
  if ! grep -q "^${k}=.\+" .env; then
    echo "Missing or empty $k in .env"
    exit 1
  fi
done

if [[ "${1:-}" == "--pull" ]]; then
  echo "Pulling images…"
  "${COMPOSE[@]}" pull
  "${COMPOSE[@]}" up -d --remove-orphans
else
  echo "Building and starting…"
  "${COMPOSE[@]}" up -d --build --remove-orphans
fi

echo "Waiting for health…"
for i in $(seq 1 40); do
  if curl -fsS http://127.0.0.1/api/health >/dev/null 2>&1; then
    curl -fsS http://127.0.0.1/api/health
    echo
    echo "OK — UI should be on http://SERVER_IP/ (then Cloudflare HTTPS)."
    exit 0
  fi
  sleep 3
done

echo "Health check did not pass. Logs:"
"${COMPOSE[@]}" logs --tail=80 app
exit 1
