import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// Blasts the ledger with charges to drive throughput, consumer lag, and the
// invariants on the dashboard. ~10% of requests deliberately replay an earlier
// idempotency key, so you can watch "double-charges prevented" climb under load.
//
//   k6 run loadtest/k6-payments.js
//   k6 run -e RATE=300 -e DURATION=2m loadtest/k6-payments.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '150');          // charges per second
const DURATION = __ENV.DURATION || '60s';
const ACCOUNT_COUNT = parseInt(__ENV.ACCOUNT_COUNT || '50'); // matches the seeded ledger
const DUP_RATE = parseFloat(__ENV.DUP_RATE || '0.1');        // fraction that replay an old key

const accepted = new Counter('charges_accepted');
const deduped = new Counter('charges_deduped');

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
  const account = `ACC-${String(1 + Math.floor(Math.random() * ACCOUNT_COUNT)).padStart(4, '0')}`;
  // Most charges are unique; some replay an old key to exercise idempotent dedupe.
  const replay = Math.random() < DUP_RATE;
  const idempotencyKey = replay
    ? `replay-${Math.floor(Math.random() * 200)}`
    : `chg-${__VU}-${__ITER}-${Date.now()}`;
  const amountCents = (1 + Math.floor(Math.random() * 80)) * 100; // $1 .. $80

  const res = http.post(`${BASE_URL}/api/payments`, JSON.stringify({
    accountId: account,
    amountCents,
    idempotencyKey,
  }), { headers: { 'Content-Type': 'application/json' } });

  const ok = check(res, { 'accepted or deduped': (r) => r.status === 202 || r.status === 200 });
  if (ok) {
    if (res.status === 200) deduped.add(1);
    else accepted.add(1);
  }
}
