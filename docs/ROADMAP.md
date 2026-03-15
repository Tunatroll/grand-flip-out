# Grand Flip Out Roadmap

## Delivery model

Use short vertical slices:

1. Research + define acceptance criteria
2. Implement one feature slice
3. Compile/lint/test gates
4. Document/release notes
5. Move to next slice

This prevents bottlenecks and keeps plugin, backend, and website aligned.

## Phase 1 - Core plugin (in progress)

- [x] Modular side panel UI
- [x] Live market API integration
- [x] Local trade analytics (FIFO/session/open positions)
- [x] GE event ingestion and dedupe
- [x] Flip logs + persistence
- [x] Hotkey framework
- [x] Versioned persistence envelopes + backward compatibility loader
- [x] Opportunity board UI + API response integration
- [x] Robust import/export for logs/trades (JSON clipboard workflow)
- [x] CSV export for logs/trades
- [x] Configurable opportunity alert notifications (margin/confidence/cooldown)
- [x] Config migration/versioning strategy (see docs/CONFIG_MIGRATION.md)

## Phase 2 - Intelligence layer

- [x] Real-time market data from OSRS Wiki prices API (cached, server-side)
- [x] Opportunity scoring: confidence = blend of volume, margin, spread stability
- [x] Explainability fields (reason string per opportunity)
- [x] Alert rules: server detects volume drops, price drift, margin collapse, stale offers across polling cycles
- [x] Strategy presets: `?strategy=low_risk|high_volume|high_margin` query param + `GET /api/market/strategies`

## Phase 3 - Website UX and onboarding

- [x] Static website scaffold in `website/` (landing, login/signup/dashboard, docs page)
- [x] Backend auth & API key spec: `docs/BACKEND_AUTH_SPEC.md`
- [x] Node/Express server in `server/`: signup, login, API key create/list/revoke, market/opportunities stubs; serves website from same process
- [x] Website wired to backend: login/signup forms and dashboard (create key, show key once)
- [x] Plugin onboarding wizard (shows in Overview tab when API key is blank)
- [x] Feature page with screenshot placeholders (website/features.html)
- [x] Support and troubleshooting page (website/support.html)

## Phase 4 - Backend hardening

- [x] Versioned API contract (`/v1/...` aliases for all routes)
- [x] Auth middleware and key scopes (JWT for web, Bearer API key for plugin)
- [x] Rate limiting per IP and per API key (in-memory sliding window)
- [x] Plan limits model (free/premium with different request caps)
- [x] Structured telemetry: request method/path/status/duration/user logged per request
- [x] Caching: Cache-Control for static assets and no-store for API; X-Response-Time header
- [x] Database persistence: optional Postgres via DATABASE_URL and store-pg.js (run db/schema.sql once)

## Phase 5 - Plugin Hub readiness

- [x] Final metadata polish (runelite-plugin.properties description; docs/COMPLIANCE_CHECKLIST.md)
- [x] Public repo hygiene (LICENSE, README present; screenshot placeholders in website/features.html)
- [x] Compliance and risk checklist (docs/COMPLIANCE_CHECKLIST.md)
- [x] Originality and attribution (docs/ORIGINALITY_AND_ATTRIBUTION.md; refs in README, COMPLIANCE_CHECKLIST, SUBMISSION_CHECKLIST, HOW_TO_SUBMIT)
- [x] Account linking and compliance (docs/ACCOUNT_LINKING_AND_COMPLIANCE.md)
- [x] Payments (docs/PAYMENTS.md; Stripe webhook + optional checkout session; plan upgrade)
- [x] Website: Pricing page, Discord links, dashboard plan display, nav/footer consistency
- [x] Discord (docs/DISCORD.md; invite placeholder; optional bot scaffold in server/scripts/discord-bot.js)
- [x] Before-release operator checklist (docs/BEFORE_RELEASE.md); COMPLIANCE see-also for account linking and payments; skip-link and main landmark on website
- [ ] Manual QA pass and test matrix (docs/QA_MANUAL_TEST.md)
- [ ] Submission to RuneLite Plugin Hub
