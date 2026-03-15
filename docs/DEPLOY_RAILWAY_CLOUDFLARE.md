# Deploying Grand Flip Out: Railway + Cloudflare

## Architecture

- **Railway** — Runs the Node.js API (auth, API keys, market data, website static files). One service.
- **Cloudflare** — Use for (a) DNS for your domain (e.g. grandflipout.com), (b) optional proxy/cache in front of Railway, (c) or host the static website on Cloudflare Pages and point API to Railway.

## Option A: All-in-one on Railway (simplest)

1. Push your repo to GitHub (or use Railway CLI).
2. In [Railway](https://railway.app): New Project → Deploy from GitHub → select repo.
3. Set **Root Directory** to `server` (so Railway runs `npm install` and `npm start` in `server/`).  
   - Or use the **Dockerfile at repo root**: in Railway, set Builder to Docker and leave root blank so it uses the root Dockerfile (API + website in one image).
4. Add a **Postgres** plugin in Railway if you want persistence; set `DATABASE_URL` in Variables. Run `server/db/schema.sql` once (Railway Postgres → Data → Query).
5. Set variables: `JWT_SECRET`, `API_RATE_LIMIT` (optional).
6. Generate domain: Settings → Generate Domain → e.g. `grandflipout-api.up.railway.app`.

**Custom domain (Cloudflare DNS):**

7. In Railway: Settings → Custom Domain → add `grandflipout.com` (or `api.grandflipout.com`).
8. In Cloudflare: DNS → Add record: Type CNAME, Name `@` (or `api`), Target the Railway-provided host (e.g. `xxx.up.railway.app`), Proxy status Off initially (or On for Cloudflare proxy).
9. Railway will show SSL; Cloudflare can then enable proxy (orange cloud) for DDoS and caching.

## Option B: Static site on Cloudflare Pages, API on Railway

1. Deploy API to Railway as above (Root Directory: `server`). Note the public URL.
2. In Cloudflare Pages: Create project → Connect to Git → select repo.
3. Build settings: **Build output directory** `website`, **Build command** leave empty (static) or `echo "No build"`. Root directory: `/`.
4. Add **Environment variable** `VITE_API_URL` or similar if your site needs the API base (for the website JS, set `API` in `website/js/app.js` to your Railway URL).
5. Custom domain: Pages → Custom domains → add `grandflipout.com`.
6. In `website/js/app.js` set `const API = 'https://your-app.up.railway.app'` so login/signup/dashboard call the API. Or use a relative URL if you proxy (see Option C).

## Option C: Cloudflare in front of Railway (one domain)

1. Deploy API + website on Railway (Dockerfile at repo root, or Root Directory `server` with website copied in).
2. Point your domain (e.g. grandflipout.com) to Railway via Cloudflare DNS (CNAME to Railway domain).
3. Enable Cloudflare proxy (orange cloud) for caching and protection. In Cloudflare → Rules → Page Rules or Cache Rules you can cache static assets (e.g. `/*.css`, `/*.js`, `/*.html`) with short TTL; leave `/api/*` and `/v1/*` uncached.

## Cache headers (API)

The server can send Cache-Control for static files and no-store for API. Response-time headers are added in code so you can monitor latency.

## Postgres on Railway

1. In Railway project: Add Postgres plugin; copy `DATABASE_URL` into your API service variables.
2. Run the schema once: Railway Postgres → Data → Query → paste and run `server/db/schema.sql`.
3. Redeploy the API service. With `DATABASE_URL` set, the server uses the Postgres store automatically (no code change).

## Checklist

- [ ] Railway: Root Directory = `server` OR Docker build from repo root
- [ ] Env: `JWT_SECRET`, optional `API_RATE_LIMIT`, `DATABASE_URL` if using Postgres
- [ ] Run `server/db/schema.sql` if using Postgres
- [ ] Cloudflare DNS: CNAME to Railway (or A/AAAA if Railway gives IP)
- [ ] Custom domain attached in Railway and in Cloudflare
- [ ] Plugin config: Server URL = your public URL (e.g. https://grandflipout.com)
