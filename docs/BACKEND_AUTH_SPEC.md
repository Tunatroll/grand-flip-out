# Backend Auth & User API Spec (Grand Flip Out)

This document describes the backend APIs needed for user accounts and API key generation so the website and plugin can support a money-generating product. Implement these on your server (e.g. grandflipout.com); the plugin and static website are clients.

## Goals

- Users sign up and log in on the website.
- Users get (or regenerate) an API key from a dashboard.
- The plugin authenticates to your market/opportunities API using that key.
- You can later add plans (free vs paid), rate limits per key, and billing.

## Recommended endpoints

### Auth (website only; not called by the plugin)

- **POST /api/auth/signup** — Register. Body: `{ "email", "password" }`. Returns session/token or redirect URL. Implement validation, hashing, and optional email verification.
- **POST /api/auth/login** — Log in. Body: `{ "email", "password" }`. Returns session cookie or JWT.
- **POST /api/auth/logout** — Invalidate session.
- **GET /api/auth/me** — Return current user profile if authenticated (e.g. `{ "id", "email", "plan" }`).

### API keys (website dashboard; plugin uses the key value only)

- **GET /api/user/keys** — List API keys for the current user (e.g. `{ "keys": [ { "id", "label", "createdAt", "lastUsedAt" } ] }`). Requires auth.
- **POST /api/user/keys** — Create a new API key. Body (optional): `{ "label" }`. Returns `{ "key": "gfo_xxxx..." }` (show once; store hash only server-side).
- **DELETE /api/user/keys/:id** — Revoke a key. Requires auth.

### Plugin-facing (already partially specified in API_CONTRACT.md)

- **GET /api/market** or **GET /v1/market/items** — Market data. Header: `Authorization: Bearer <apiKey>`. Validate key, optionally rate-limit per key, return items (+ optional opportunities).
- **GET /api/opportunities** or **GET /v1/market/opportunities** — Ranked opportunities. Same auth.

Key validation: on each request, resolve the Bearer token to a user (or key record). If invalid or revoked, return 401.

## Data model (suggested)

- **users** — id, email, password_hash, plan (e.g. free|premium), created_at, updated_at.
- **api_keys** — id, user_id, key_hash (never store raw key), label, created_at, last_used_at.

Generate keys as secure random strings (e.g. 32 bytes, hex or base64). Store only a hash (e.g. SHA-256) so a DB leak does not expose keys.

## Security

- HTTPS only.
- Do not log or expose raw API keys.
- Rate limit by IP and by key (e.g. per-minute request caps).
- Use secure session/JWT handling for the website; same-origin and httpOnly cookies recommended for web.

## Monetization hooks

- **plan** on user: free (e.g. 60 req/min, basic opportunities) vs premium (higher limits, more data or features).
- **GET /api/user/plan** — Return current plan and limits so the plugin or website can show upgrade prompts.
- Billing: integrate Stripe/paddle/etc. for premium; set `plan` after payment.

## Website integration

- Login/signup pages POST to `/api/auth/login` and `/api/auth/signup`; on success redirect to dashboard.
- Dashboard calls `GET /api/user/keys` and `POST /api/user/keys` to list and create keys; display the new key once (copy button).
- Plugin does not call auth endpoints; it only uses the API key in the Authorization header for market/opportunities.

All of this stays on your backend. The plugin and static site in this repo only consume the key and document the flow; no secrets are stored in the repo.
