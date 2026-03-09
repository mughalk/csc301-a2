# Load Generator — `load-gen.js`

Uses k6 to load test our services with concurrent GET and POST requests. 

- VU = "virtual user"; each one runs in a loop sending requests for the duration of the test.
- they will be sending variable POST and GETs
- From piazza @146: 
  - "for component 2, you are in charge of starting up all the services. 
  - When you come to my office for the evaluation, you get to tell me the IP address (a lab machine) 
  - and port of the "entry point" into your system where I can send all GET and POST requests for our API to, 
  - and you get to tell me a different lab machine's IP address where at least one of your DB(s) is.
  - Component 2: you are in charge of starting it up, you just tell me 1 IP address to direct all testing traffic to."
- basically need to be able to run the load test against a remote VM, but can also run it locally for testing and profiling purposes. The `TARGET` environment variable controls this (defaults to `http://localhost:14002` if not set).
- need a services machine and a DB machine
---

## Install k6

```bash
brew install k6          # macOS
sudo apt-get install k6  # Linux
```

## Start the services

**Option A — local (runme.sh):**
```bash
./runme.sh -r   # wipe databases
./runme.sh -c   # compile
./runme.sh -a   # start all 4 services (keep this terminal open)
```

**Option B — Docker:**
```bash
docker-compose up --build
```

## Run it

Once services are up, in a new terminal:

```bash
k6 run load-gen.js                              # defaults: 100 VUs, 1s, localhost:14002
k6 run -e VUS=500 -e DURATION=30s load-gen.js  # custom VUs/duration but ran on LOCAL
k6 run -e TARGET=http://<ip>:14002 -e VUS=500 -e DURATION=30s load-gen.js  # remote VM; replace <ip> with the VM's IP address
```

---

## Marking tiers

Each tier is "N requests handled successfully within 1 second". Use `DURATION=5s` for more stable numbers when profiling locally.

```bash
# running the load tests locally (and hence no TARGET variable defined or needed)
k6 run -e VUS=10   -e DURATION=5s load-gen.js
k6 run -e VUS=25   -e DURATION=5s load-gen.js
k6 run -e VUS=50   -e DURATION=5s load-gen.js
k6 run -e VUS=100  -e DURATION=5s load-gen.js
k6 run -e VUS=175  -e DURATION=5s load-gen.js
k6 run -e VUS=250  -e DURATION=5s load-gen.js
k6 run -e VUS=500  -e DURATION=5s load-gen.js
k6 run -e VUS=750  -e DURATION=5s load-gen.js
k6 run -e VUS=1000 -e DURATION=5s load-gen.js
k6 run -e VUS=2500 -e DURATION=5s load-gen.js
k6 run -e VUS=4000 -e DURATION=5s load-gen.js

# against the VM (demo day; our VM is dh2010pc44)
k6 run -e TARGET=http://142.1.46.113:14002 -e VUS=500 -e DURATION=5s load-gen.js
```

---

## What to look at in the output

```
http_reqs/s              — throughput; this is what we're graded on (check a2instructions.txt for more info)
http_req_duration p(95)  — must stay under 1000ms
http_req_failed          — must stay under 5%
```