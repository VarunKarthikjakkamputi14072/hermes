"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
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
  CheckCircle2,
  CreditCard,
  Layers,
  RefreshCw,
  ShieldCheck,
  XCircle,
  Zap,
} from "lucide-react";
import {
  charge,
  fetchAccount,
  HAS_LIVE_BACKEND,
  ledgerStreamUrl,
  type LedgerMetrics,
} from "@/lib/api";

const ACCOUNT = "ACC-0001";
const CHARGE_CENTS = 2_500; // $25
const ACCOUNT_COUNT = 50;

const EMPTY: LedgerMetrics = {
  timestamp: 0,
  pending: 0,
  applied: 0,
  rejected: 0,
  total: 0,
  appliedPerSec: 0,
  duplicatesBlocked: 0,
  totalDebitedCents: 0,
  accountsOverdrawn: 0,
};

const usd = (cents: number) => `$${(cents / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const rid = () => (crypto.randomUUID ? crypto.randomUUID() : String(Math.random()));

type Point = { t: number; pending: number };

export default function PaymentsPage() {
  const live = HAS_LIVE_BACKEND;
  const [m, setM] = useState<LedgerMetrics>(EMPTY);
  const [balance, setBalance] = useState<number | null>(null);
  const [history, setHistory] = useState<Point[]>([]);
  const [busy, setBusy] = useState(false);
  const demo = useRef({ applied: 0, rejected: 0, dupes: 0, debited: 0, balance: 97_200 });

  const refreshBalance = useCallback(async () => {
    if (!live) {
      setBalance(demo.current.balance);
      return;
    }
    const a = await fetchAccount(ACCOUNT);
    if (a) setBalance(a.balanceCents);
  }, [live]);

  // ---- LIVE: stream the ledger over SSE (no polling) -----------------------
  useEffect(() => {
    if (!live) return;
    const url = ledgerStreamUrl();
    if (!url) return;
    const es = new EventSource(url);
    es.addEventListener("ledger", (ev) => {
      const next: LedgerMetrics = JSON.parse((ev as MessageEvent).data);
      setM(next);
      setHistory((h) => [...h, { t: next.timestamp, pending: next.pending }].slice(-40));
    });
    es.onerror = () => {}; // EventSource auto-reconnects
    return () => es.close();
  }, [live]);

  useEffect(() => {
    refreshBalance();
  }, [refreshBalance]);

  // ---- DEMO: light client-side model so the page is alive without a backend
  const demoTick = useCallback(() => {
    const d = demo.current;
    setM({
      ...EMPTY,
      applied: d.applied,
      rejected: d.rejected,
      total: d.applied + d.rejected,
      duplicatesBlocked: d.dupes,
      totalDebitedCents: d.debited,
      accountsOverdrawn: 0,
    });
    setBalance(d.balance);
    setHistory((h) => [...h, { t: Date.now(), pending: 0 }].slice(-40));
  }, []);

  const chargeOnce = useCallback(async () => {
    setBusy(true);
    if (live) {
      await charge(ACCOUNT, CHARGE_CENTS, `web-${rid()}`);
    } else {
      const d = demo.current;
      if (d.balance >= CHARGE_CENTS) { d.balance -= CHARGE_CENTS; d.applied += 1; d.debited += CHARGE_CENTS; }
      else d.rejected += 1;
      demoTick();
    }
    await refreshBalance();
    setTimeout(() => setBusy(false), 300);
  }, [live, refreshBalance, demoTick]);

  // The money shot: one idempotency key, fired 100x at once → charged ONCE.
  const doubleTap = useCallback(async () => {
    setBusy(true);
    if (live) {
      const key = `dup-${rid()}`; // SAME key for every request
      await Promise.all(Array.from({ length: 100 }, () => charge(ACCOUNT, CHARGE_CENTS, key)));
    } else {
      const d = demo.current;
      if (d.balance >= CHARGE_CENTS) { d.balance -= CHARGE_CENTS; d.applied += 1; d.debited += CHARGE_CENTS; }
      d.dupes += 99; // 99 of the 100 are deduped
      demoTick();
    }
    await refreshBalance();
    setTimeout(() => setBusy(false), 600);
  }, [live, refreshBalance, demoTick]);

  const blackFriday = useCallback(async () => {
    setBusy(true);
    if (live) {
      await Promise.all(Array.from({ length: 200 }, () => {
        const acct = `ACC-${String(1 + Math.floor(Math.random() * ACCOUNT_COUNT)).padStart(4, "0")}`;
        const amt = (1 + Math.floor(Math.random() * 80)) * 100;
        return charge(acct, amt, `bf-${rid()}`);
      }));
    } else {
      const d = demo.current;
      for (let i = 0; i < 200; i++) {
        const amt = (1 + Math.floor(Math.random() * 80)) * 100;
        if (d.balance >= amt && Math.random() > 0.4) { d.balance -= amt; d.applied += 1; d.debited += amt; }
        else d.rejected += 1;
      }
      demoTick();
    }
    await refreshBalance();
    setTimeout(() => setBusy(false), 900);
  }, [live, refreshBalance, demoTick]);

  return (
    <div className="grid-bg min-h-screen">
      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div className="flex flex-col gap-2">
            <div className="section-title flex items-center gap-2">
              <Layers className="h-4 w-4" />
              Hermes · Payments Ledger
            </div>
            <h1 className="text-3xl font-bold tracking-tight text-slate-50">
              Double-charge stress test
            </h1>
            <p className="max-w-2xl text-sm text-slate-400">
              Every charge is a real <span className="mono text-slate-300">POST /api/payments</span> →{" "}
              <span className="text-terminal-accent">202 Accepted</span>. The worker debits the
              account under a row lock; a client-supplied <span className="mono text-slate-300">Idempotency-Key</span>{" "}
              guarantees a charge is applied <span className="text-terminal-accent">exactly once</span> — even
              fired 100× at once.
            </p>
          </div>
          <div className="flex flex-col items-end gap-2">
            <span className={`badge ${live ? "bg-emerald-500/15 text-emerald-300" : "bg-amber-500/15 text-amber-300"}`}>
              <span className="h-1.5 w-1.5 rounded-full bg-current animate-pulse-dot" />
              {live ? "LIVE · order-api" : "DEMO · simulated ledger"}
            </span>
            <Link href="/" className="text-xs text-slate-500 hover:text-terminal-accent">
              flash-sale demo →
            </Link>
          </div>
        </header>

        <div className="mt-8 grid gap-6 lg:grid-cols-[1.1fr,1fr]">
          {/* Charge card */}
          <section className="panel overflow-hidden">
            <div className="panel-header">
              <div className="flex items-center gap-2">
                <CreditCard className="h-4 w-4 text-terminal-accent" />
                <span className="mono text-sm text-slate-100">{ACCOUNT}</span>
              </div>
              <button onClick={refreshBalance} className="text-slate-500 hover:text-terminal-accent" title="refresh balance">
                <RefreshCw className="h-4 w-4" />
              </button>
            </div>
            <div className="space-y-5 p-5">
              <div>
                <div className="label">Account balance</div>
                <div className="mono text-4xl font-bold text-terminal-accent">
                  {balance == null ? "—" : usd(balance)}
                </div>
              </div>
              <div className="flex flex-wrap gap-3">
                <button onClick={chargeOnce} disabled={busy} className="btn-primary">
                  <CreditCard className="h-4 w-4" />
                  Charge {usd(CHARGE_CENTS)}
                </button>
                <button onClick={doubleTap} disabled={busy} className="btn-ghost border-amber-500/40 text-amber-300 hover:border-amber-400 hover:text-amber-200">
                  <Zap className="h-4 w-4" />
                  {busy ? "Firing…" : "Double-tap ×100 (same key)"}
                </button>
                <button onClick={blackFriday} disabled={busy} className="btn-ghost">
                  <Activity className="h-4 w-4" />
                  Black Friday · 200 charges
                </button>
              </div>
              <p className="text-xs text-slate-500">
                <strong className="text-slate-300">Double-tap</strong> fires 100 charges with one
                idempotency key — a flaky client retrying. The balance drops by {usd(CHARGE_CENTS)}{" "}
                <em>once</em>; the other 99 are blocked. Watch{" "}
                <span className="text-amber-300">double-charges prevented</span> jump.
              </p>
            </div>
          </section>

          {/* Ledger status */}
          <section className="panel">
            <div className="panel-header">
              <div className="flex items-center gap-2">
                <Activity className="h-4 w-4 text-terminal-accent" />
                <h2 className="text-sm font-semibold text-slate-100">Ledger status</h2>
              </div>
              <span className="mono text-xs text-slate-500">{usd(m.totalDebitedCents)} settled</span>
            </div>
            <div className="grid grid-cols-3 gap-px bg-terminal-border">
              <Stat icon={<CheckCircle2 className="h-4 w-4" />} label="Applied" value={m.applied} tone="text-emerald-300" />
              <Stat icon={<XCircle className="h-4 w-4" />} label="Rejected" value={m.rejected} tone="text-red-300" />
              <Stat icon={<ShieldCheck className="h-4 w-4" />} label="Dbl-charges blocked" value={m.duplicatesBlocked} tone="text-amber-300" />
            </div>
            <div className="p-4">
              <div className="mb-3 flex items-center justify-between rounded-md border border-emerald-500/30 bg-emerald-500/10 px-3 py-2">
                <span className="text-xs font-semibold text-emerald-300">INVARIANT · accounts overdrawn</span>
                <span className={`mono text-sm font-bold ${m.accountsOverdrawn === 0 ? "text-emerald-300" : "text-red-400"}`}>
                  {m.accountsOverdrawn}
                </span>
              </div>
              <div className="mb-2 flex items-center justify-between">
                <span className="label mb-0">Ledger queue depth</span>
                <span className="mono text-xs text-slate-500">{Math.round(m.appliedPerSec)} /s · last 40 ticks</span>
              </div>
              <div className="h-36 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={history} margin={{ top: 4, right: 4, bottom: 0, left: -28 }}>
                    <defs>
                      <linearGradient id="lq" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#22d3a3" stopOpacity={0.6} />
                        <stop offset="100%" stopColor="#22d3a3" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <XAxis dataKey="t" hide />
                    <YAxis tick={{ fill: "#475569", fontSize: 11 }} axisLine={false} tickLine={false} width={48} />
                    <Tooltip
                      contentStyle={{ background: "#0f151d", border: "1px solid #1f2a37", borderRadius: 8, fontSize: 12 }}
                      labelFormatter={() => "queue depth"}
                      formatter={(v: number) => [v, "pending"]}
                    />
                    <Area type="monotone" dataKey="pending" stroke="#22d3a3" strokeWidth={2} fill="url(#lq)" isAnimationActive={false} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
          </section>
        </div>

        <footer className="mt-8 text-center text-xs text-slate-600">
          {live ? (
            <>Streaming the ledger over Server-Sent Events from order-api · 500ms push.</>
          ) : (
            <>Demo mode — set <span className="mono">NEXT_PUBLIC_HERMES_API_BASE_URL</span> to drive the real Spring Boot + Kafka ledger.</>
          )}
        </footer>
      </div>
    </div>
  );
}

function Stat({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: number; tone: string }) {
  return (
    <div className="bg-terminal-panel px-4 py-4">
      <div className="flex items-center gap-1.5 text-xs text-slate-400">
        <span className={tone}>{icon}</span>
        {label}
      </div>
      <div className={`mono mt-1 text-2xl font-bold ${tone}`}>{value.toLocaleString()}</div>
    </div>
  );
}
