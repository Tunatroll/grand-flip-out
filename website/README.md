# Grand Flip Out — Website

Static site for landing, docs, auth, dashboard, and pricing. When you run the server from `server/`, it serves this folder so one deployment (e.g. Railway) serves both the API and the site.

## Contents

- **index.html** — Landing, features, CTAs
- **docs.html** — Plugin setup (API key, config)
- **login.html** / **signup.html** — Auth (wired to backend)
- **dashboard.html** — API key management and plan
- **pricing.html** — Plans and upgrade (Stripe Checkout when configured)
- **support.html** — Troubleshooting and Discord
- **styles.css** — Shared styles (dark theme)

## Discord invite

Replace `discord.gg/grandflipout` with your server invite in all HTML files (nav and footer). See **docs/DISCORD.md**.

## Deploy

- **With the server:** Deploy the Node server (e.g. Railway); set `WEBSITE_DIR` to `../website` or leave default. The server serves the API and the site from one origin.
- **Static only:** Host `website/` on Netlify, Vercel, or Cloudflare Pages; set the site’s API base to your backend URL (e.g. in `js/app.js` if you use a separate API origin).

## No secrets

Do not put API keys or secrets in this folder. Auth and keys are handled by the backend.
