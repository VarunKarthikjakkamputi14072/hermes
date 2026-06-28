// Thin client over the Hermes order-api. When NEXT_PUBLIC_HERMES_API_BASE_URL
// is unset the UI falls back to DEMO mode (see app/page.tsx), so the queue
// visualization is alive without booting Kafka + Postgres.

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_HERMES_API_BASE_URL?.replace(/\/$/, "") ?? "";

export const HAS_LIVE_BACKEND = API_BASE_URL.length > 0;

export type OrderStats = {
  PENDING: number;
  FULFILLED: number;
  REJECTED: number;
};

// One frame off the Spring Boot SSE stream (GET /api/metrics/stream).
export type LiveMetrics = {
  timestamp: number;
  pending: number;
  fulfilled: number;
  rejected: number;
  total: number;
  acceptedPerSec: number;
  featuredSku: string | null;
  featuredStock: number | null;
};

// URL for the SSE metrics stream, or null in DEMO mode (no live backend).
export function metricsStreamUrl(sku?: string): string | null {
  if (!HAS_LIVE_BACKEND) return null;
  const q = sku ? `?sku=${encodeURIComponent(sku)}` : "";
  return `${API_BASE_URL}/api/metrics/stream${q}`;
}

// ---- Ledger / payments -----------------------------------------------------

// One frame off the ledger SSE stream (GET /api/payments/stream).
export type LedgerMetrics = {
  timestamp: number;
  pending: number;
  applied: number;
  rejected: number;
  total: number;
  appliedPerSec: number;
  duplicatesBlocked: number;
  totalDebitedCents: number;
  accountsOverdrawn: number;
};

export type Account = { id: string; holder: string; balanceCents: number };

export function ledgerStreamUrl(): string | null {
  if (!HAS_LIVE_BACKEND) return null;
  return `${API_BASE_URL}/api/payments/stream`;
}

export async function fetchAccount(id: string): Promise<Account | null> {
  if (!HAS_LIVE_BACKEND) return null;
  try {
    const res = await fetch(`${API_BASE_URL}/api/accounts/${id}`, { cache: "no-store" });
    if (!res.ok) return null;
    const d = await res.json();
    return { id: String(d.id), holder: String(d.holder), balanceCents: Number(d.balanceCents) };
  } catch {
    return null;
  }
}

// A risk flag raised by the deterministic rules; narrative is the AI analyst note
// (NVIDIA NIM), null/pending until generated off-thread.
export type FraudFlag = {
  id: string;
  accountId: string;
  amountCents: number;
  riskScore: number;
  decision: "ALLOW" | "REVIEW" | "HOLD";
  reasons: string;
  narrative: string | null;
  narrativePending: boolean;
  createdAt: string;
};

export async function fetchFraudFlags(limit = 12): Promise<FraudFlag[] | null> {
  if (!HAS_LIVE_BACKEND) return null;
  try {
    const res = await fetch(`${API_BASE_URL}/api/fraud/flags?limit=${limit}`, { cache: "no-store" });
    if (!res.ok) return null;
    const d = await res.json();
    return Array.isArray(d) ? (d as FraudFlag[]) : null;
  } catch {
    return null;
  }
}

// Fire one charge. Reusing an idempotencyKey is guaranteed not to charge twice.
export async function charge(
  accountId: string,
  amountCents: number,
  idempotencyKey: string,
): Promise<{ ok: boolean; status: number; deduplicated: boolean }> {
  if (!HAS_LIVE_BACKEND) return { ok: true, status: 202, deduplicated: false };
  try {
    const res = await fetch(`${API_BASE_URL}/api/payments`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ accountId, amountCents, idempotencyKey }),
    });
    let deduplicated = false;
    try {
      deduplicated = (await res.json())?.deduplicated === true;
    } catch {
      /* ignore body parse */
    }
    return { ok: res.ok, status: res.status, deduplicated };
  } catch {
    return { ok: false, status: 0, deduplicated: false };
  }
}

export type Product = {
  sku: string;
  name: string;
  category: string;
  price: number;
  stockAvailable: number;
};

export type DeadLetter = {
  orderId: string;
  sku: string;
  quantity: number;
  reason: string;
  // AI-written incident ticket (NVIDIA NIM). May be null if not yet generated.
  ticket: string | null;
  createdAt: string;
};

const EMPTY_STATS: OrderStats = { PENDING: 0, FULFILLED: 0, REJECTED: 0 };

export async function fetchStats(): Promise<OrderStats | null> {
  if (!HAS_LIVE_BACKEND) return null;
  try {
    const res = await fetch(`${API_BASE_URL}/api/orders/stats`, { cache: "no-store" });
    if (!res.ok) return null;
    const d = await res.json();
    return {
      PENDING: Number(d.PENDING ?? 0),
      FULFILLED: Number(d.FULFILLED ?? 0),
      REJECTED: Number(d.REJECTED ?? 0),
    };
  } catch {
    return null;
  }
}

export async function fetchProducts(): Promise<Product[] | null> {
  if (!HAS_LIVE_BACKEND) return null;
  try {
    const res = await fetch(`${API_BASE_URL}/api/products`, { cache: "no-store" });
    if (!res.ok) return null;
    const d = await res.json();
    if (!Array.isArray(d)) return null;
    return d.map((p: Record<string, unknown>) => ({
      sku: String(p.sku ?? ""),
      name: String(p.name ?? ""),
      category: String(p.category ?? ""),
      price: Number(p.price ?? 0),
      stockAvailable: Number(p.stockAvailable ?? 0),
    }));
  } catch {
    return null;
  }
}

export async function placeOrder(
  sku: string,
  quantity: number,
): Promise<{ ok: boolean; status: number }> {
  if (!HAS_LIVE_BACKEND) return { ok: true, status: 202 };
  try {
    const res = await fetch(`${API_BASE_URL}/api/orders`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        customerId: `web-${Math.random().toString(36).slice(2, 10)}`,
        sku,
        quantity,
      }),
    });
    return { ok: res.ok, status: res.status };
  } catch {
    return { ok: false, status: 0 };
  }
}

export async function fetchDeadLetters(): Promise<DeadLetter[] | null> {
  if (!HAS_LIVE_BACKEND) return null;
  try {
    const res = await fetch(`${API_BASE_URL}/api/orders/dlq`, { cache: "no-store" });
    if (!res.ok) return null;
    const d = await res.json();
    if (!Array.isArray(d)) return null;
    return d as DeadLetter[];
  } catch {
    return null;
  }
}

export { EMPTY_STATS };
