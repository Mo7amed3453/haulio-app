# Haulio Backend – Traffic Data Layer

Node.js 20 / Fastify backend for the Haulio Real-Time Traffic Avoidance system.  
Deployed on **Hetzner CCX33** (4 vCPU / 16 GB RAM).

## Architecture

```
src/
├── index.ts                       # Fastify entry point
├── env.ts                         # Zod-validated env
├── lib/
│   └── supabase.ts                # Supabase client + shared types
├── services/
│   └── speed-tiles/
│       └── SpeedTileUpdater.ts    # 10-min cron: crowd-sources + TomTom → Valhalla .spd
├── routes/
│   ├── incidents.ts               # POST/GET /v1/incidents
│   ├── zones.ts                   # GET /v1/zones
│   ├── budget.ts                  # GET /v1/budget/tomtom
│   └── reroute.ts                 # POST /v1/reroute-event
├── realtime/
│   └── incidents.ts               # Supabase Realtime subscription helper
├── jobs/
│   ├── expireIncidents.ts         # Hourly expiry cron
│   └── confirmationAggregator.ts  # 5-min verification cron
└── scripts/
    ├── seed-cli.ts                # CLI entry: npm run seed
    ├── seed-school-zones.ts
    ├── seed-industrial-zones.ts
    ├── seed-rail-crossings.ts
    ├── seed-historical-corridors.ts
    └── overpass.ts                # Shared Overpass API client

supabase/migrations/
├── 0001_traffic_events.sql
├── 0002_extreme_zones.sql
├── 0003_tomtom_budget.sql
├── 0004_driver_telemetry.sql
└── 0005_reroute_events.sql
```

## Prerequisites

- Node 20+
- A Supabase project with PostGIS enabled
- TomTom API key (max 2000 calls/day; backend uses ≤200)
- Valhalla routing engine (optional, for live traffic injection)

## Environment Variables

Copy `.env.example` to `.env` and fill in values:

| Variable | Description |
|----------|-------------|
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_SERVICE_KEY` | Service role key (bypasses RLS) |
| `TOMTOM_API_KEY` | TomTom Developer API key |
| `VALHALLA_LIVE_TRAFFIC_DIR` | Path where Valhalla reads `.spd` tiles |
| `VALHALLA_RELOAD_URL` | Valhalla admin reload endpoint (optional) |
| `PORT` | HTTP listen port (default: 3000) |

## Local Development

```bash
cd backend
npm install
cp .env.example .env   # fill in secrets
npm run dev            # ts-node watch mode
```

## Run Tests

```bash
npm test               # vitest run
npm run test:coverage  # with v8 coverage
```

## Supabase Migrations

Apply migrations in order using the Supabase CLI:

```bash
supabase db push
# or manually:
psql "$DATABASE_URL" -f supabase/migrations/0001_traffic_events.sql
psql "$DATABASE_URL" -f supabase/migrations/0002_extreme_zones.sql
psql "$DATABASE_URL" -f supabase/migrations/0003_tomtom_budget.sql
psql "$DATABASE_URL" -f supabase/migrations/0004_driver_telemetry.sql
psql "$DATABASE_URL" -f supabase/migrations/0005_reroute_events.sql
```

## Zone Seeding

```bash
# Schools in California
npm run seed -- --state CA --type schools

# Industrial zones with explicit bbox
npm run seed -- --bbox 32.5,-117.2,33.9,-116.0 --type industrial

# Rail crossings (fetches GTFS from transit.land)
npm run seed -- --state TX --type rails

# Historical corridors (reads driver_telemetry)
npm run seed -- --type corridors

# All types
npm run seed -- --state CA --type all
```

Supported `--state` shortcuts: CA, TX, NY, FL  
For other regions use `--bbox minLat,minLng,maxLat,maxLng`.

## Docker

```bash
# Build
docker build -t haulio-backend .

# Run (set env file)
docker run -d \
  --env-file .env \
  -p 3000:3000 \
  -v /data/valhalla/live_traffic:/data/valhalla/live_traffic \
  --name haulio-backend \
  haulio-backend
```

---

## Hetzner CCX33 Deployment Guide

### 1. Provision Server

- **Type**: CCX33 (4 vCPU AMD / 16 GB RAM / 240 GB NVMe)
- **Image**: Ubuntu 24.04
- **Firewall**: Allow ports 22 (SSH), 3000 (API), 80/443 (optional reverse proxy)

### 2. Install Node.js 20

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
node --version  # v20.x.x
```

### 3. Clone & Build

```bash
git clone https://github.com/Mo7amed3453/haulio-app.git /opt/haulio
cd /opt/haulio/backend
npm ci
npm run build
```

### 4. Create Environment File

```bash
sudo mkdir -p /etc/haulio
sudo nano /etc/haulio/backend.env
# Paste env vars (see .env.example)
sudo chmod 600 /etc/haulio/backend.env
```

### 5. Create Valhalla Directory

```bash
sudo mkdir -p /data/valhalla/live_traffic
sudo chown -R haulio:haulio /data/valhalla
```

### 6. Create Systemd Service

```bash
sudo useradd -r -s /bin/false haulio

sudo tee /etc/systemd/system/haulio-backend.service > /dev/null <<'EOF'
[Unit]
Description=Haulio Backend Traffic Service
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=haulio
Group=haulio
WorkingDirectory=/opt/haulio/backend
EnvironmentFile=/etc/haulio/backend.env
ExecStart=/usr/bin/node dist/index.js
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=haulio-backend

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/data/valhalla/live_traffic
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable haulio-backend
sudo systemctl start haulio-backend
sudo systemctl status haulio-backend
```

### 7. Check Logs

```bash
journalctl -u haulio-backend -f --output=cat
```

### 8. Health Check

```bash
curl http://localhost:3000/health
curl http://localhost:3000/health/speed-tiles
```

### 9. Updating

```bash
cd /opt/haulio
git pull origin main
cd backend
npm ci
npm run build
sudo systemctl restart haulio-backend
```

---

## API Reference

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Server liveness |
| GET | `/health/speed-tiles` | SpeedTileUpdater last run status |
| POST | `/v1/incidents` | Report a traffic event (auth required) |
| GET | `/v1/incidents?bbox=...` | List active events in bounding box |
| GET | `/v1/zones?bbox=...` | List extreme zones for offline bundle |
| GET | `/v1/budget/tomtom` | Today's TomTom API usage |
| POST | `/v1/reroute-event` | Log AutoReroute activation (analytics) |

### POST /v1/incidents

```json
{
  "type": "ACCIDENT",
  "lat": 37.7749,
  "lng": -122.4194,
  "severity": "HIGH"
}
```

Requires `Authorization: Bearer <supabase-jwt>`.
