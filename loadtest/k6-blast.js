import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// Blasts the order API to drive Kafka throughput and consumer lag on the
// Grafana dashboard. Run with:
//   k6 run loadtest/k6-blast.js
// Tune intensity with env vars, e.g.:
//   k6 run -e RATE=500 -e DURATION=2m loadtest/k6-blast.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '200');        // orders per second
const DURATION = __ENV.DURATION || '60s';
const SKU_COUNT = parseInt(__ENV.SKU_COUNT || '200'); // matches the seeded catalogue

const accepted = new Counter('orders_accepted');

export const options = {
  scenarios: {
    blast: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(50, Math.ceil(RATE / 4)),
      maxVUs: Math.max(200, RATE),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const sku = `SKU-${String(1 + Math.floor(Math.random() * SKU_COUNT)).padStart(4, '0')}`;
  const payload = JSON.stringify({
    customerId: `cust-${Math.floor(Math.random() * 100000)}`,
    sku: sku,
    quantity: 1 + Math.floor(Math.random() * 5),
  });

  const res = http.post(`${BASE_URL}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  const ok = check(res, { 'accepted (202)': (r) => r.status === 202 });
  if (ok) accepted.add(1);
}
