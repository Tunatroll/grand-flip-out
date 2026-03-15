# Submit Day Runbook

Use this in order on release day. This is the shortest safe path from "ready" to "submitted + monetization live".

---

## 1) Final local preflight (2 minutes)

From repo root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\publish-preflight.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\check-infra.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-prod.ps1
```

Resolve any **FAIL** items. Warnings are allowed, but you should resolve all warnings you care about before public launch.

One-command alternative:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\go-live-status.ps1
```

Or full operator flow (applies Discord/Stripe if provided, then runs all checks):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\operator-launch.ps1 `
  -InviteUrl "https://discord.gg/yourInviteCode" `
  -StripeSecretKey "sk_live_..." `
  -StripePriceId "price_..." `
  -StripeWebhookSecret "whsec_..." `
  -RedeployAfterStripe
```

---

## 2) Replace website Discord placeholder (30 seconds)

If your invite is ready:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\replace-discord-invite.ps1 -InviteUrl "https://discord.gg/yourInviteCode"
```

Then run preflight again.

---

## 3) Enable paid checkout in Railway (5 minutes)

In Railway, service `gfo-server`, set:

- `STRIPE_SECRET_KEY`
- `STRIPE_PRICE_ID`
- `BASE_URL` = `https://grandflipout.com`

If using webhooks:

- `STRIPE_WEBHOOK_SECRET`

After setting vars, redeploy `gfo-server`.

Quickest safe CLI path:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\set-stripe-vars.ps1 `
  -StripeSecretKey "sk_live_..." `
  -StripePriceId "price_..." `
  -StripeWebhookSecret "whsec_..." `
  -Redeploy
```

Quick check:

- `GET https://grandflipout.com/api/checkout/config` should return `{"configured":true}`

If `www.grandflipout.com` is not resolving, get the exact Cloudflare DNS target from Railway:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\get-www-dns-target.ps1
```

---

## 4) Visual listing quality (manual)

- Take at least 1 clear plugin panel screenshot (Overview/Live Market/Trade History).
- Put it in `screenshots/` and link it from `README.md`.

---

## 5) Submission package sanity

- `runelite-plugin.properties` has final:
  - `displayName`
  - `author`
  - `description`
  - `tags`
  - `plugins`
- No competitor wording in metadata/docs intended for submission.
- Build still passes.

---

## 6) Submit to RuneLite Plugin Hub

Follow `docs/HOW_TO_SUBMIT.md`.

Keep PR concise and reviewer-friendly:
- what the plugin does
- compliance statement (analysis/UI only, no automation)
- where backend/API is documented

---

## 7) Post-submit operations

- Monitor Railway logs for auth/market/checkout errors.
- Monitor Discord support channel.
- Keep changelog clear and human.
- Run smoke checks after each deploy:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-prod.ps1
```

---

If you follow this runbook in order, you avoid almost all last-minute submission and launch surprises.
