# Ecosystem demo — the self-healing RAG loop

Boots Hermes + Transit + Meridian on one network so the full loop runs in a
single `docker compose up`. Verified end-to-end 2026-06-14.

```
query --> Transit (gateway) --taps telemetry--> Meridian (RAG drift)
                                                     |
                                              drift breach
                                                     v
Qdrant <--embeds-- Hermes worker <--re-embed job-- order-api
   ^                                                  ^
   |                                                  |
   +--- the apps read what Hermes wrote --------------+
```

Observe on the read path (Transit → Meridian), act on the write path
(Meridian → Hermes). Transit serves **real** answers via NVIDIA NIM (free-tier
key from `Transit/.env`); Meridian builds its drift reference from observed
warm-up traffic.

## Layout assumption

This file lives in `hermes/ecosystem-demo/`. It builds Hermes from `..` and
expects the sibling repos checked out next to `hermes`:

```
Documents/
  hermes/        <- this repo (build context `..`)
  Transit/       <- build context `../../Transit`
  meridian/      <- build context `../../meridian`
```

## Run it

```bash
cd hermes/ecosystem-demo
docker compose up -d --build      # first build is slow (Maven + Evidently images)
```

Host ports: order-api `8080`, transit `18080`, rag-monitor `8002`, qdrant `6333`.

## Drive the single trace

```bash
# 1. mint an API key on Transit
KEY=$(curl -s -X POST localhost:18080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@x.dev","password":"demo-pass-123"}' \
  | sed -E 's/.*"api_key":"([^"]+)".*/\1/')

# 2. warm up the baseline with short, on-corpus queries (then snapshot it)
for i in $(seq 1 90); do
  curl -s -o /dev/null -X POST localhost:18080/api/v1/chat/completions \
    -H "Authorization: Bearer $KEY" -H 'Content-Type: application/json' \
    -d '{"messages":[{"role":"user","content":"What is the patient HbA1c value?"}]}'
done
curl -s -X POST localhost:8002/reference/build      # reference = observed baseline

# 3. cause drift with long, off-corpus queries (use a NEW key — see gotcha)
#    then watch the metrics climb and Qdrant grow:
curl -s localhost:8002/metrics | grep -E 'rag_drift_share|reembeds_triggered'
curl -s -X POST localhost:6333/collections/hermes-chunks/points/count \
  -H 'Content-Type: application/json' -d '{"exact":true}'
```

On drift, `meridian_rag_dataset_drift` flips to 1, `meridian_reembeds_triggered_total`
increments, a Hermes ingestion job completes, and `hermes-chunks` fills with
points whose payload `docId` is `rag-corpus`.

## Gotchas

- **Rate limit:** Transit's free tier is 100 requests/hour **per key**. Use a
  separate `/auth/register` key for the warm-up vs the drift phase, or raise
  `FREE_TIER_REQUESTS_PER_HOUR`.
- **Real embedding model:** set `MODEL_NAME=all-MiniLM-L6-v2` on
  `embedding-service` to swap the deterministic stand-in for a real model.
- The three repos are unmerged feature branches: hermes `ingestion-bus`,
  Transit `meridian-telemetry-tap`, meridian `rag-drift-monitor`.
