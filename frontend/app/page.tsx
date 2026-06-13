"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  Area,
  AreaChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  Activity,
  AlertTriangle,
  Boxes,
  CheckCircle2,
  Cpu,
  Layers,
  ShoppingCart,
  Ticket,
  XCircle,
  Zap,
} from "lucide-react";
import {
  fetchDeadLetters,
  HAS_LIVE_BACKEND,
  metricsStreamUrl,
  placeOrder,
  type DeadLetter,
  type LiveMetrics,
  type OrderStats,
} from "@/lib/api";

const FEATURED = {
  sku: "AURORA-GA3",
  name: "Aurora Festival — Front-Row Pass",
  category: "live-events",
  price: 249.0,
  initialStock: 5000,
};

type DepthPoint = { t: number; pending: number };

const DEMO_TICKETS = [
  "Worker failed to fulfil order after 3 retries. Root cause: optimistic-lock contention on row `AURORA-GA3` under burst load — the pessimistic lock timed out (50ms) while a sibling worker held it. Impact: 1 order parked, no overselling occurred. Recommended action: safe to replay once burst subsides; consider raising lock timeout to 200ms for flash-sale SKUs.",
  "Order rejected by fulfilment worker: requested quantity (4) exceeds remaining stock (2) for `AURORA-GA3`. This is expected backpressure at sell-out, not a defect. Recommended action: do NOT replay — notify customer the tier is sold out and offer the next tier.",
  "Deserialization error on OrderPlacedEvent: `quantity` field was null. Likely a malformed client request that bypassed API validation. Impact: poison message isolated to DLQ, partition kept flowing. Recommended action: discard; add a not-null guard at the producer edge.",
];

export default function Page() {
  const live = HAS_LIVE_BACKEND;

  const [stats, setStats] = useState<OrderStats>({
    PENDING: 0,
    FULFILLED: 0,
    REJECTED: 0,
  });
  const [stock, setStock] = useState(FEATURED.initialStock);
  const [history, setHistory] = useState<DepthPoint[]>([]);
  const [deadLetters, setDeadLetters] = useState<DeadLetter[]>([]);
  const [flashing, setFlashing] = useState(false);

  // Demo simulator state lives in refs so the interval closure stays stable.
  const demo = useRef({ pending: 0, fulfilled: 0, rejected: 0, stock: FEATURED.initialStock });

  // ---- LIVE mode: stream metrics over SSE (no polling) ----------------------
  // The Spring Boot order-api pushes a snapshot every 500ms over Server-Sent
  // Events. We update React state the instant a frame arrives, so the charts
  // react in real time when k6 (or the flash-sale button) fires.
  useEffect(() => {
    if (!live) return;
    const url = metricsStreamUrl(FEATURED.sku);
    if (!url) return;

    let lastRejected = -1;
    const refreshDlq = async () => {
      const dlq = await fetchDeadLetters();
      if (dlq) setDeadLetters(dlq.slice(0, 6));
    };

    const es = new EventSource(url);
    es.addEventListener("metrics", (ev) => {
      const m: LiveMetrics = JSON.parse((ev as MessageEvent).data);
      setStats({ PENDING: m.pending, FULFILLED: m.fulfilled, REJECTED: m.rejected });
      setHistory((h) => [...h, { t: m.timestamp, pending: m.pending }].slice(-40));
      if (typeof m.featuredStock === "number") setStock(m.featuredStock);
      // Event-driven DLQ refresh: pull fresh incident tickets only when a new
      // rejection/dead-letter actually appears — still no polling timer.
      if (lastRejected === -1 || m.rejected > lastRejected) refreshDlq();
      lastRejected = m.rejected;
    });
    // EventSource reconnects automatically on a dropped connection.
    es.onerror = () => {};

    return () => es.close();
  }, [live]);

  // ---- DEMO mode: client-side simulator so the pipeline is alive -----------
  useEffect(() => {
    if (live) return;
    const id = setInterval(() => {
      const d = demo.current;
      // Workers drain the queue at their own pace (decoupling): up to 80/tick.
      const drained = Math.min(d.pending, Math.floor(Math.random() * 80) + 20);
      for (let i = 0; i < drained; i++) {
        if (d.stock > 0) {
          d.stock -= 1;
          d.fulfilled += 1;
        } else {
          d.rejected += 1;
        }
      }
      d.pending -= drained;

      setStats({ PENDING: d.pending, FULFILLED: d.fulfilled, REJECTED: d.rejected });
      setStock(d.stock);
      setHistory((h) => [...h, { t: Date.now(), pending: d.pending }].slice(-40));
    }, 600);
    return () => clearInterval(id);
  }, [live]);

  const buy = useCallback(
    async (qty: number, count = 1) => {
      if (live) {
        await Promise.all(
          Array.from({ length: count }, () => placeOrder(FEATURED.sku, qty)),
        );
        return;
      }
      // Demo: drop orders straight into the queue (API returns 202 instantly).
      demo.current.pending += qty * count;
      setStats((s) => ({ ...s, PENDING: demo.current.pending }));
    },
    [live],
  );

  const flashSale = useCallback(async () => {
    setFlashing(true);
    await buy(1, 5000); // 50,000-style spike, scaled for the demo
    if (!live) {
      // Seed a couple of DLQ incidents to show the poison-message path.
      const now = Date.now();
      setDeadLetters((prev) =>
        [
          {
            orderId: crypto.randomUUID(),
            sku: FEATURED.sku,
            quantity: 1,
            reason: "lock-timeout after 3 retries",
            ticket: DEMO_TICKETS[0],
            createdAt: new Date(now).toISOString(),
          },
          {
            orderId: crypto.randomUUID(),
            sku: FEATURED.sku,
            quantity: 4,
            reason: "insufficient stock",
            ticket: DEMO_TICKETS[1],
            createdAt: new Date(now - 1500).toISOString(),
          },
          ...prev,
        ].slice(0, 6),
      );
    }
    setTimeout(() => setFlashing(false), 1200);
  }, [buy, live]);

  const total = stats.PENDING + stats.FULFILLED + stats.REJECTED;
  const soldPct = Math.min(
    100,
    Math.round(((FEATURED.initialStock - stock) / FEATURED.initialStock) * 100),
  );

  return (
    <div className="grid-bg min-h-screen">
      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
        {/* Header */}
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div className="flex flex-col gap-2">
            <div className="section-title flex items-center gap-2">
              <Layers className="h-4 w-4" />
              Hermes · Order Fulfillment Engine
            </div>
            <h1 className="text-3xl font-bold tracking-tight text-slate-50">
              Live drop console
            </h1>
            <p className="max-w-2xl text-sm text-slate-400">
              Every click is a real <span className="mono text-slate-300">POST /api/orders</span> →{" "}
              <span className="text-terminal-accent">202 Accepted</span> in ~5ms. Kafka holds the
              line; workers drain it and take a pessimistic row-lock so the same ticket is never
              oversold.
            </p>
          </div>
          <span
            className={`badge ${
              live
                ? "bg-emerald-500/15 text-emerald-300"
                : "bg-amber-500/15 text-amber-300"
            }`}
          >
            <span className="h-1.5 w-1.5 rounded-full bg-current animate-pulse-dot" />
            {live ? "LIVE · order-api" : "DEMO · simulated pipeline"}
          </span>
        </header>

        <div className="mt-8 grid gap-6 lg:grid-cols-[1.1fr,1fr]">
          {/* Drop card */}
          <section className="panel overflow-hidden">
            <div className="panel-header">
              <div className="flex items-center gap-2">
                <Ticket className="h-4 w-4 text-terminal-accent" />
                <span className="mono text-sm text-slate-100">{FEATURED.sku}</span>
              </div>
              <span className="badge bg-slate-700/40 text-slate-300">
                ${FEATURED.price.toFixed(2)}
              </span>
            </div>
            <div className="space-y-5 p-5">
              <div>
                <h2 className="text-xl font-semibold text-slate-50">{FEATURED.name}</h2>
                <p className="text-sm text-slate-400">{FEATURED.category}</p>
              </div>

              <div>
                <div className="flex items-end justify-between">
                  <div>
                    <div className="label">Remaining</div>
                    <div className="mono text-4xl font-bold text-terminal-accent">
                      {stock.toLocaleString()}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="label">Sold</div>
                    <div className="mono text-lg text-slate-200">{soldPct}%</div>
                  </div>
                </div>
                <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-terminal-bg">
                  <div
                    className="h-full rounded-full bg-terminal-accent transition-all duration-500"
                    style={{ width: `${soldPct}%` }}
                  />
                </div>
              </div>

              <div className="flex flex-wrap gap-3">
                <button onClick={() => buy(1)} className="btn-primary">
                  <ShoppingCart className="h-4 w-4" />
                  Buy now
                </button>
                <button onClick={() => buy(1, 100)} className="btn-ghost">
                  <ShoppingCart className="h-4 w-4" />
                  Buy ×100
                </button>
                <button
                  onClick={flashSale}
                  disabled={flashing}
                  className="btn-ghost border-amber-500/40 text-amber-300 hover:border-amber-400 hover:text-amber-200"
                >
                  <Zap className="h-4 w-4" />
                  {flashing ? "Flooding…" : "Flash sale · 5,000 at once"}
                </button>
              </div>
              <p className="text-xs text-slate-500">
                The flash-sale button fires thousands of orders in one burst. Watch the queue
                spike then drain — the API never blocks, so the storefront never freezes.
              </p>
            </div>
          </section>

          {/* Warehouse status */}
          <section className="panel">
            <div className="panel-header">
              <div className="flex items-center gap-2">
                <Activity className="h-4 w-4 text-terminal-accent" />
                <h2 className="text-sm font-semibold text-slate-100">Warehouse status</h2>
              </div>
              <span className="mono text-xs text-slate-500">{total.toLocaleString()} orders</span>
            </div>
            <div className="grid grid-cols-3 gap-px bg-terminal-border">
              <StatCell
                icon={<Boxes className="h-4 w-4" />}
                label="Pending"
                value={stats.PENDING}
                tone="text-amber-300"
              />
              <StatCell
                icon={<CheckCircle2 className="h-4 w-4" />}
                label="Fulfilled"
                value={stats.FULFILLED}
                tone="text-emerald-300"
              />
              <StatCell
                icon={<XCircle className="h-4 w-4" />}
                label="Rejected"
                value={stats.REJECTED}
                tone="text-red-300"
              />
            </div>
            <div className="p-4">
              <div className="mb-2 flex items-center justify-between">
                <span className="label mb-0">Kafka queue depth</span>
                <span className="mono text-xs text-slate-500">last 40 ticks</span>
              </div>
              <div className="h-40 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={history} margin={{ top: 4, right: 4, bottom: 0, left: -28 }}>
                    <defs>
                      <linearGradient id="q" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#22d3a3" stopOpacity={0.6} />
                        <stop offset="100%" stopColor="#22d3a3" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <XAxis dataKey="t" hide />
                    <YAxis
                      tick={{ fill: "#475569", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                      width={48}
                    />
                    <Tooltip
                      contentStyle={{
                        background: "#0f151d",
                        border: "1px solid #1f2a37",
                        borderRadius: 8,
                        fontSize: 12,
                      }}
                      labelFormatter={() => "queue depth"}
                      formatter={(v: number) => [v, "pending"]}
                    />
                    <Area
                      type="monotone"
                      dataKey="pending"
                      stroke="#22d3a3"
                      strokeWidth={2}
                      fill="url(#q)"
                      isAnimationActive={false}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
          </section>
        </div>

        {/* DLQ / Problem desk */}
        <section className="panel mt-6">
          <div className="panel-header">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 text-amber-400" />
              <h2 className="text-sm font-semibold text-slate-100">
                Dead-letter queue · AI incident tickets
              </h2>
            </div>
            <span className="badge bg-slate-700/40 text-slate-300">
              <Cpu className="h-3 w-3" /> NVIDIA NIM
            </span>
          </div>
          <div className="p-4">
            {deadLetters.length === 0 ? (
              <p className="py-6 text-center text-sm text-slate-500">
                No dead letters. Poison messages (bad data, lock timeouts) get parked here after 3
                retries — the partition keeps flowing. Run a flash sale to see the path.
              </p>
            ) : (
              <ul className="space-y-3">
                {deadLetters.map((dl) => (
                  <li
                    key={dl.orderId}
                    className="animate-fade-in rounded-lg border border-terminal-border bg-terminal-bg/60 p-4"
                  >
                    <div className="mb-2 flex flex-wrap items-center gap-2 text-xs">
                      <span className="badge bg-red-500/15 text-red-300">DLT</span>
                      <span className="mono text-slate-300">{dl.sku}</span>
                      <span className="text-slate-500">qty {dl.quantity}</span>
                      <span className="text-slate-600">·</span>
                      <span className="text-slate-500">{dl.reason}</span>
                      <span className="ml-auto mono text-slate-600">
                        {dl.orderId.slice(0, 8)}
                      </span>
                    </div>
                    <p className="text-sm leading-relaxed text-slate-300">
                      {dl.ticket ?? (
                        <span className="text-slate-500">Generating incident ticket…</span>
                      )}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <footer className="mt-8 text-center text-xs text-slate-600">
          {live ? (
            <>Streaming live metrics over Server-Sent Events from order-api · 500ms push.</>
          ) : (
            <>
              Demo mode — set <span className="mono">NEXT_PUBLIC_HERMES_API_BASE_URL</span> to drive
              the real Spring Boot + Kafka pipeline.
            </>
          )}
        </footer>
      </div>
    </div>
  );
}

function StatCell({
  icon,
  label,
  value,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  tone: string;
}) {
  return (
    <div className="bg-terminal-panel px-4 py-4">
      <div className="flex items-center gap-1.5 text-xs text-slate-400">
        <span className={tone}>{icon}</span>
        {label}
      </div>
      <div className={`mono mt-1 text-2xl font-bold ${tone}`}>
        {value.toLocaleString()}
      </div>
    </div>
  );
}
