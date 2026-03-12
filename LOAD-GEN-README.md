# Load Generator — `load-gen.js`

Uses k6 to load test our services with concurrent GET and POST requests.

- VU = "virtual user"; each one runs in a loop sending requests for the duration of the test
- Each iteration sends one random request (GET user, GET product, GET orders, POST order, POST user, POST product)
- **Read `iterations/s` from the summary — this is your TPS. Do NOT use `http_reqs/s`** (that counts internal proxy hops and inflates ~3x)
- From Piazza @146:
  - "for component 2, you are in charge of starting up all the services."
  - "you get to tell me the IP address and port of the entry point into your system"
  - "you get to tell me a different lab machine's IP address where at least one of your DBs is"

---

## Infrastructure

```
Your laptop  →  SSH tunnel  →  Host: dh2010pc44.utm.utoronto.ca (142.1.46.113)
                                       ↓ port forward
                               VM1: 10.128.1.113  — Java services (OrderService on :8080)
                                       ↓ internal network
                               VM2: 10.128.2.113  — PostgreSQL DB (always running)
```

- **Testing endpoint:** `142.1.46.113:8080` → forwarded to VM1 OrderService
- **DB lives on VM2** and persists independently — data survives service restarts
- **VM1 exposed ports:** 8080-8085, 4000-4005 (everything else is firewalled)

---

## Install k6

```bash
brew install k6          # macOS
sudo apt-get install k6  # Linux
```

---

## SSH into VM1 (to start/stop services)

```bash
# Step 1: SSH to the host machine
ssh <utorid>@dh2010pc44.utm.utoronto.ca

# Step 2: Hop into VM1
ssh -p 2222 student@localhost   # password: test123
```

To reach VM2 from VM1:
```bash
ssh student@10.128.2.113        # password: test123
```

---

## Starting services on VM1

Pull latest code first:
```bash
cd ~/csc301-a2
git pull
```

### Option A — Docker

```bash
docker-compose -f docker-compose.vm1.yml up --build -d

# Check status:
docker-compose -f docker-compose.vm1.yml ps

# Watch logs:
docker-compose -f docker-compose.vm1.yml logs -f

# Stop:
docker-compose -f docker-compose.vm1.yml down
```

### Option B — runme.sh

```bash
# Compile (only needed after code changes):
./runme.sh -c

# Start all 4 services (keep terminal open, Ctrl+C to stop):
DB_URL=jdbc:postgresql://10.128.2.113:5432/a2db \
DB_USER=a2user \
DB_PASS=a2pass \
./runme.sh -a
```

Run in background instead (no terminal needed):
```bash
DB_URL=jdbc:postgresql://10.128.2.113:5432/a2db DB_USER=a2user DB_PASS=a2pass \
  nohup ./runme.sh -a > services.log 2>&1 &

# Stop background services:
pkill -9 -f "java" 2>/dev/null
```

### Verify services are up

```bash
curl http://localhost:8080/user/0   # should return {} with 200 or 404
```

---

## Run load tests from your local machine

The VM firewall blocks port 8080 from outside. Use an SSH tunnel.

### Step 1 — Open tunnel (one terminal, leave it open)

```bash
./scripts/tunnel.sh <utorid>
# OR manually:
ssh -fNT -o ServerAliveInterval=30 -L 8080:localhost:8080 <utorid>@dh2010pc44.utm.utoronto.ca
```

### Step 2 — Run k6

```bash
# Against VM (accurate, matches our OH measurement [150 TPS average]):
k6 run -e TARGET=http://localhost:8080 -e VUS=20 -e DURATION=30s load-gen.js

# Locally (fast but too high and inacurate, only use this for quick sanity checks!!!):
k6 run -e VUS=20 -e DURATION=30s load-gen.js
```

### Close tunnel when done

```bash
pkill -f "ssh -fNT.*8080:localhost"
```

---

## Marking tiers

Each tier = "N requests handled successfully within 1 second". Use `DURATION=30s` for stable averages or 10s for quick runs.

```bash
# Against VM (use these for real measurements):
k6 run -e TARGET=http://localhost:8080 -e VUS=20  -e DURATION=30s load-gen.js
k6 run -e TARGET=http://localhost:8080 -e VUS=50  -e DURATION=30s load-gen.js
k6 run -e TARGET=http://localhost:8080 -e VUS=100 -e DURATION=30s load-gen.js
```

Tier table:

| Tier | TPS target | Marks |
|------|-----------|-------|
| 0    | 2         | 0.2   |
| 1    | 10        | 1.0   |
| 2    | 25        | 1.0   |
| 3    | 50        | 1.0   |
| 4    | 100       | 1.0   |
| 5    | 175       | 1.0   |
| 6    | 250       | 1.0   |
| 7    | 500       | 1.0   |
| 8    | 750       | 1.0   |
| 9    | 1000      | 1.0   |
| 10   | 2500      | 1.0   |
| 11   | 4000      | 1.0   |
| +    | highest throughput (any group) | 1 bonus |

---

## What to look at in the output

```
iterations/s         — YOUR TPS (one iteration = one transaction)
http_req_failed      — must stay under 5%
```

---

## Checking the database (for demo)

```bash
ssh <utorid>@dh2010pc44.utm.utoronto.ca
ssh -p 2222 student@localhost     # password: test123
ssh student@10.128.2.113          # password: test123
psql -U a2user -d a2db -h localhost -W   # password: a2pass
```
