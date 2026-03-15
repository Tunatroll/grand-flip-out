# Grand Flip Out — API Server

Node.js/Express backend for auth, API keys, and market/opportunities endpoints. Deploy to Railway (or any Node host) and point the plugin and website at it.

## Endpoints

- **POST /api/auth/signup** — `{ "email", "password" }` (min 8 chars). Returns user + token/cookie.
- **POST /api/auth/login** — `{ "email", "password" }`. Returns user + token/cookie.
- **POST /api/auth/logout** — Clears cookie.
- **GET /api/auth/me** — Current user (requires cookie or `Authorization: Bearer <jwt>`).
- **GET /api/user/keys** — List API keys (requires web auth).
- **POST /api/user/keys** — Create API key; returns `{ "key": "gfo_..." }` once (requires web auth).
- **DELETE /api/user/keys/:id** — Revoke key (requires web auth).
- **GET /api/market** — Real-time market data + ranked opportunities from OSRS Wiki prices (requires `Authorization: Bearer <api_key>`). Plugin calls this.
- **GET /api/opportunities** — Ranked opportunities only (requires API key). Plugin fallback.
- **GET /api/user/plan** — Current user plan and limits (requires web auth).
- **GET /api/checkout/config** — Returns `{ configured: boolean }` to indicate whether premium checkout is live.
- **POST /api/checkout/session** — Create Stripe Checkout Session (requires web auth; set STRIPE_SECRET_KEY, STRIPE_PRICE_ID). Returns `{ url }`.
- **POST /api/webhooks/stripe** — Stripe webhook for payment events (set STRIPE_WEBHOOK_SECRET; install optional `stripe`). Upgrades user to `premium` on success.
- **GET /health** — Health check.
- **GET /api/health** — API health check alias.

Market data is fetched live from the OSRS Wiki Real-Time Prices API (`prices.runescape.wiki`), cached for 60 seconds, and scored by the server-side ranking algorithm. Opportunities are sorted by a blend of confidence (volume + margin stability) and margin percentage.

Rate limiting is applied per API key (default 60 req/min; set `API_RATE_LIMIT` env var). Auth endpoints are limited to 15 req/min per IP.

## Run locally

```bash
cd server
npm install
npm run dev
```

Default port 3000. Set `PORT` and `JWT_SECRET` in env if needed. The same server serves the **website** (login, signup, dashboard, docs) from the `../website` folder. Open http://localhost:3000 to sign up, log in, create an API key, then paste the key into the RuneLite plugin config (Server URL: http://localhost:3000, API key: the created key).

## Deploy to Railway

1. In Railway: New Project → Deploy from GitHub (or CLI).
2. Root or subfolder: set **Root Directory** to `server` if the repo root is the plugin.
3. Set environment variables in Railway dashboard:
   - **PORT** — Railway sets this automatically.
   - **JWT_SECRET** — Use a long random string for production (e.g. `openssl rand -base64 32`).
   - **API_RATE_LIMIT** — Optional; requests per minute per API key (default 60).
4. Optional: **DATABASE_URL** for Postgres (see Data persistence below). Optional: **STRIPE_*** and **BASE_URL** for payments (see docs/PAYMENTS.md).

## Plugin config

- **Server URL**: `https://your-app.railway.app` (or your custom domain).
- **Market endpoint path**: `/api/market`
- **Opportunities endpoint path**: `/api/opportunities`
- **API Key**: Create one from the dashboard after signup; paste into plugin config.

## Data persistence

- **In-memory (default):** No `DATABASE_URL`; data is lost on restart.
- **Postgres:** Set `DATABASE_URL` (e.g. from Railway Postgres). Run `db/schema.sql` once. The server uses `store-pg.js` and optional dependency `pg` automatically. Run `npm install` so `pg` is installed.
