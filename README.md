# Surviving Traffic Bursts with Adaptive Concurrency in gRPC (Java 17)

This repository contains a reproducible Java 17 gRPC demo that shows:
- why static rate limits fail under traffic bursts
- how queue buildup causes p95/p99 latency collapse
- how latency-driven adaptive concurrency limits (Netflix concurrency-limits / Vegas) stabilize tail latency with controlled rejections
- how to observe and explain the behavior using Prometheus + Grafana

---

## Quickstart (copy/paste)

From repo root:

1) Build
- ./gradlew clean build

2) Start Prometheus + Grafana
- docker-compose up

3) Run gRPC server (new terminal)
- ./gradlew :service:server:run

4) Run load generator client (new terminal)
- ./gradlew :service:client:run

Open:
- Grafana: http://localhost:3000  (default login: admin / admin)
- Prometheus: http://localhost:9090

Stop:
- docker-compose down

---

## Repository Layout

- proto/                  gRPC protobufs
- service/server/         gRPC server + bounded executor + limiter + metrics
- service/client/         async (non-blocking) load generator
- ops/prometheus/         Prometheus scrape config (scrapes /metrics)
- ops/grafana/            Grafana provisioning + dashboards
- docker-compose.yml      Prometheus + Grafana stack

---

## What This Demo Proves (the story)

Baseline (limiter disabled):
- burst causes executor queue buildup
- p95/p99 latency rises sharply and can plateau (backlog persists)
- errors/rejections spike late, after latency is already bad

With adaptive concurrency enabled (Vegas):
- limiter reduces allowed concurrency proactively (based on latency)
- p95/p99 stays stable (or increases modestly)
- controlled rejections occur (instead of uncontrolled queueing)
- recovery is fast once burst ends (no lingering backlog)

---

## Grafana / Prometheus Queries (copy/paste)

### 1) gRPC latency percentiles (milliseconds)

Metric:
- grpc_server_latency_ms_bucket (histogram buckets)
- labels: service, method, status

Panel: “gRPC Latency (ms)” (add 3 queries to the same panel)

p50:
- histogram_quantile(0.50, sum by (le) (rate(grpc_server_latency_ms_bucket[1m])))

p95:
- histogram_quantile(0.95, sum by (le) (rate(grpc_server_latency_ms_bucket[1m])))

p99:
- histogram_quantile(0.99, sum by (le) (rate(grpc_server_latency_ms_bucket[1m])))

Optional (per-method p99, one line per method):
- histogram_quantile(0.99, sum by (method, le) (rate(grpc_server_latency_ms_bucket[1m])))

### 2) Adaptive concurrency limit (Vegas)

Metric:
- drl_vegas_limit (gauge)

Panel: “Adaptive Concurrency Limit”
- max(drl_vegas_limit)

### 3) Limiter outcomes (success / rejected / dropped / etc) as req/s

Metric:
- drl_counters_total (counter)
- label: statusvalue in {success, rejected, dropped, ignored, bypassed}

Panel: “Limiter Outcomes (req/s)” (single query; one line per statusvalue)
- sum by (statusvalue) (rate(drl_counters_total[1m]))

Optional panels:
Rejected only (req/s):
- sum(rate(drl_counters_total{statusvalue="rejected"}[1m]))

Acceptance ratio (%):
- 100 * sum(rate(drl_counters_total{statusvalue="success"}[1m])) / sum(rate(drl_counters_total[1m]))

---

## Before vs After Runbook (for repeatable screenshots)

1) Start Grafana/Prometheus
- docker-compose up

2) Run server
- ./gradlew :service:server:run

3) Run client to generate warmup → steady → burst → recovery
- ./gradlew :service:client:run

4) Screenshot these panels:
- gRPC Latency p99 (ms)
- Adaptive Concurrency Limit
- Limiter Outcomes (req/s)

Repeat the same steps with limiter disabled vs enabled and capture “before/after”.

---

## Troubleshooting

No data in Grafana:
- confirm server is running
- confirm Prometheus is scraping the server’s /metrics endpoint
- check Prometheus targets: http://localhost:9090/targets

Port conflicts:
- docker-compose down
- stop any previous server/client runs
- rerun docker-compose up and the server/client commands

---

## Contributing

See CONTRIBUTING.md.

---

## License

Apache License 2.0
