# Before you release (operator checklist)

One place for everything only you can do before going live or submitting to the Plugin Hub. Tick as you go.

---

## Plugin Hub submission

- [ ] Set **author** in `runelite-plugin.properties` (your name or team).
- [ ] Add at least one **screenshot** of the panel; put it in the README or `screenshots/` and reference it.
- [ ] Run through [QA_MANUAL_TEST.md](QA_MANUAL_TEST.md) on a clean profile.
- [ ] Run `scripts/publish-preflight.ps1` and resolve any **FAIL** items.
- [ ] Submit via RuneLite’s process. See [HOW_TO_SUBMIT.md](HOW_TO_SUBMIT.md).

---

## Website and API

- [ ] **Deploy** the server (e.g. Railway). Set `PORT`, `JWT_SECRET`; optionally `DATABASE_URL`, Stripe vars. See [DEPLOY_RAILWAY_CLOUDFLARE.md](DEPLOY_RAILWAY_CLOUDFLARE.md).
- [ ] Replace **Discord** invite: search for `discord.gg/grandflipout` in `website/*.html` and replace with your server invite. See [DISCORD.md](DISCORD.md).
- [ ] Or run: `scripts/replace-discord-invite.ps1 -InviteUrl "https://discord.gg/yourInviteCode"` to replace all website placeholders.
- [ ] Optional: Configure **Stripe** (keys, webhook URL, product/price). See [PAYMENTS.md](PAYMENTS.md).
- [ ] Optional: Run the **Discord bot** (`server/scripts/discord-bot.js`) with `DISCORD_BOT_TOKEN` if you want `!help` / `!status` in your server. See [DISCORD.md](DISCORD.md).

---

## Quick reference

| Item | Where |
|------|--------|
| Author | `runelite-plugin.properties` |
| Screenshot | README or `screenshots/` |
| Manual QA | [QA_MANUAL_TEST.md](QA_MANUAL_TEST.md) |
| Submit | [HOW_TO_SUBMIT.md](HOW_TO_SUBMIT.md) |
| Deploy | [DEPLOY_RAILWAY_CLOUDFLARE.md](DEPLOY_RAILWAY_CLOUDFLARE.md) |
| Discord link | All `website/*.html` |
| Stripe | [PAYMENTS.md](PAYMENTS.md), server env vars |
| Discord bot | [DISCORD.md](DISCORD.md), `server/scripts/discord-bot.js` |

Nothing in this list is automated; it all needs you. The rest of the repo is ready.
