# Server Monitoring Dashboard — Living Design Doc

> ⚠️ **TEMPORARY DOC.** This is a working design reference and checklist for the
> server monitoring/admin-dashboard feature. It is intended to be **deleted once
> the feature is complete and merged.** Do not treat it as permanent documentation.

## Goal

Give the server admin in-app visibility into how the server is being used:
API usage & performance, persistent/per-user errors, security (brute-force /
repeated login failures), and live log tailing — all surfaced in the existing
`/admin` web UI, with optional alerting via email.

## Locked design decisions

- **In-app, Postgres-backed.** No external services required to get value. An
  optional, off-by-default Prometheus endpoint is the only external hook.
- **Config lives on the existing Settings page** via `AdminServerConfig` /
  `ConfigRepository`. A single `MonitoringConfig` (JSON-serialized, like
  `PatreonConfig`) holds all toggles + retention windows.
- **Defaults:** collection **ON**, Prometheus **OFF**, alerting **OFF** (email
  may be unconfigured), PII-sensitive data short-lived. **Master kill-switch**
  disables everything (and hides the whole Monitoring nav group).
- **Retention is configurable** (defaults: metrics 30d, errors 90d, login
  attempts 30d).
- **Information architecture:** one grouped **Monitoring** nav item (hidden when
  the master toggle is off, like Patreon/Email) →
  `Overview · Performance · Errors · Security · Logs`. No nav alert badge.
- **Data strategy:** in-memory accumulation → rolled-up hourly/daily buckets;
  additive latency **histogram buckets** (`<50/100/250/500/1000/2500/∞ ms`) for
  approximate p95/p99; **deduplicated** errors (fingerprint + count); bounded
  in-memory log ring buffer. Collection is async/batched, a **no-op when
  disabled**, and keyed by **route template** (not concrete path) to avoid
  cardinality blowups from `{userId}`/`{projectName}`.
- **Design system:** "Writer's Desk" (`docs/WEB-DESIGN-SYSTEM.md`), BEM. Reuse
  `paper-section`, `card-list`, `pagination`, `empty-state`, semantic severity
  colors. New components (stat cards, alert cards, log viewer) built via the
  `/frontend-design` skill and folded back into the design system.
- **Charting:** **frappe-charts** (SVG, zero-dep, MIT) **vendored** into
  `server/src/main/resources/assets/` (no CDN, no JS build step). Used for
  charts + sparklines on Overview/Performance.

## Overview page layout (alerts-first)

1. **Alerts panel** (hero) — severity-coded, deep-link to the relevant deep-dive
   filtered to the incident. Empty state: "✓ All systems nominal".
2. **Stat cards** — Requests 24h · Error rate · p95 latency · Active syncs (live).
3. **Live row** — req/min sparkline + active sync sessions (HTMX poll).
4. **Mini-charts** — requests/errors over time, top slowest endpoints (launchpads
   into Performance).

Alerts = curated/thresholded ("abnormal"); Errors page = full firehose.

---

## Implementation phases & checklist

Each phase is independently mergeable; commit per phase to
`claude/server-admin-dashboard-TveU5`.

### Phase 1 — Foundation (backend/data only, no UI design needed)
- [x] `MonitoringConfig` data class + `AdminServerConfig.MONITORING_CONFIG` key
- [x] `.sq` schema: `api_metric_bucket`, `error_log`, `login_attempt` (no migration — Postgres schema unshipped, so `Schema.create` covers everyone)
- [x] DAOs (`ApiMetricDao`, `ErrorLogDao`, `LoginAttemptDao`) + adapters in `ServerDatabaseFactory`
- [x] Repositories (`MetricsRepository`, `ErrorRepository`, `SecurityRepository`)
- [x] Koin wiring in `mainModule.kt`
- [x] `MonitoringMaintenanceJob` + `configureMonitoringJob()` (rollup / purge), wired in `appMain`
- [x] Tests: config round-trip + DAO rollup/dedupe/purge (`MonitoringConfigTest`, `MonitoringDaoTest`)
- [→] Monitoring config fields on the Settings page — **moved to the first UI phase** so it goes through `/frontend-design`. Config works via defaults until then.

> ⚠️ **Not yet compiled/tested locally.** This remote environment's network policy
> can't reach Google's Maven, so the Android Gradle plugin won't resolve and Gradle
> can't configure the (multi-module) project — even for a `:server`-only task. The
> server changes are pure JVM/SQLDelight; they need a build + test run in CI or on a
> dev machine to verify (especially SQLDelight codegen of the new `.sq` files).

### Phase 2 — API metrics + Overview UI
**Part A — data pipeline (done):**
- [x] `MetricsCollector` singleton (atomic accumulator + non-cumulative latency histogram + bounded live ring buffer; no-op when disabled)
- [x] Custom Ktor timing plugin `apiMetricsPlugin` (keyed by matched route template via `RoutingCall.route`), wired in `appMain` as `configureApiMetrics()`
- [x] Flush + cadence in `MonitoringMaintenanceJob` (60s flush, 1h rollup/purge; syncs collector enabled-flag from live config)
- [x] `MetricsRepository` aggregation: `getEndpointStats` / `getTotals` + additive-histogram `percentile()`
- [x] `SyncSessionManager.activeSessionCount()`
- [x] Tests: `MetricsCollectorTest` (binning/drain/disabled/percentile), `MetricsRepositoryTest` (aggregation through the DB)

**Part B — UI (done, via `/frontend-design`):**
- [x] frappe-charts via CDN (the proxy here can't vendor it, and the app already CDN-loads htmx + Font Awesome, so this matches precedent — swap to a vendored asset later if a CDN-free admin page is wanted)
- [x] `monitoring-nav.mustache` sub-nav + Monitoring item in `admin-nav` + routing (`MonitoringPage.kt`)
- [x] Monitoring config fields on the Settings page (`admin-settings.mustache` + `serverSettingsRoutes`)
- [x] **Overview** page (derived alerts, stat cards, traffic chart, slowest endpoints) — `monitoring.css`
- [x] **Performance** page (per-endpoint table, percentiles, 24h/7d/30d range)
- Aesthetic: extended the "Writer's Desk" system (typewriter labels, paper cards, amber/ink, method chips).
- Deviation from plan: Monitoring nav item is always visible (gating it per-page would mean threading a runtime flag through every admin model); the Overview shows an in-page "disabled" notice instead when the master toggle is off.

> ⚠️ Templates/CSS are not exercised by CI (which compiles Kotlin + runs tests but
> doesn't render Mustache). Kotlin wiring is CI-verified; the pages themselves need
> a manual look in a running server.

### Phase 3 — Errors + alerting (done)
- [x] StatusPages `exception<Throwable>` hook → `recordMonitoredError(...)` → `ErrorRepository.record` (fingerprint dedupe, route template + user_id)
- [x] `MonitoringState` cached-flag holder (job refreshes each tick) so the error recorder never hits the DB on the request path
- [x] **Errors** page (`/admin/monitoring/errors`): grouped, expandable stack traces, paginated
- [x] Alert evaluation in the maintenance job → email via `EmailService` (occurrence threshold, `notified_at` dedupe, 24h window)
- [x] Tests: alert query (`MonitoringDaoTest`), email-alert flow (`MonitoringAlertTest`)
- **DI note:** monitoring deps are **constructor-injected** through `adminPage` (consistent with the rest of the code). The route tests get inert monitoring beans from the shared `setupKtorTestKoin` helper — one place, no per-test churn.

### Phase 4 — Security
**4A — login tracking + Security page (done):**
- [x] Login-attempt recording in `AccountRoutes.login()` (IP via `request.origin.remoteAddress`, gated by cached `MonitoringState` flags `loginTrackingEnabled`/`storeLoginIp`)
- [x] `getTopFailingEmails` query + **Security** page (`/admin/monitoring/security`): top failed-login accounts (24h) + recent attempts table with OK/FAIL badges
- [x] Test: `getTopFailingEmails` ranking/window

**4B — rate limiting (done):**
- [x] `ktor-server-rate-limit` installed in `configureSecurity`; `/api/account/login` wrapped in `rateLimit(LOGIN_RATE_LIMIT)`, keyed by source host
- [x] Limit is an **injected** `LoginRateLimitConfig` (prod default 10/min) — the test base injects an effectively-unlimited one so login tests never trip
- Note: relies on the standard Ktor plugin behavior; existing login tests verify the wiring doesn't break login. A dedicated "trips at N+1" test could be added with a low-limit override.

### Phase 5 — Logs (done)
- [x] **Switched logging backend to Logback** (was slf4j-simple, with logback commented out) so appenders work — `logback.xml` activated, root kept at INFO to match prior effective level
- [x] Bounded `LogRingBuffer` (in-memory, capacity 1000) + `RingBufferLogAppender` registered in `logback.xml`, with best-effort secret redaction (Bearer/token/password)
- [x] **Logs** page (`/admin/monitoring/logs`): HTMX-polled live tail (every 3s, toggleable) with level + text filters
- [x] Test: ring-buffer filtering/bounding + redaction
- Note: used HTMX polling rather than SSE (no SSE infra in the app); swap to SSE later if a true push stream is wanted.

### Phase 6 — Optional Prometheus — **ON HOLD**
Parked on branch `claude/server-monitoring-prometheus-endpoint` (reverted from this branch). See that branch for the implementation.

---

## Notes / open items
- Confirm where StatusPages is installed (Phase 3 hook point).
- Decide error fingerprint dimensions: `(exception_type, route)` baseline; add
  `user_id` for sync-specific errors to answer "one user vs. everyone".
- Login attempt IP storage is the most PII-sensitive bit — gated by
  `storeLoginIp`, purged on `loginAttemptRetentionDays`.
