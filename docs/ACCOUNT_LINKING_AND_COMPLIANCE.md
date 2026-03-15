# Account linking and compliance

## How users link their account

Users do **not** link a RuneScape or Jagex account. They link their **Grand Flip Out** account to the plugin using an API key.

1. **Sign up** — User goes to your site (e.g. grandflipout.com), signs up with **email + password**. Stored on your server only.
2. **Log in** — Same site, log in. They get a session cookie (and optional JWT).
3. **Create API key** — In the dashboard they click "Create new API key". The key is shown once; they copy it.
4. **Paste in RuneLite** — In RuneLite: Configuration (wrench) → **Grand Flip Out API** → Server URL (e.g. `https://grandflipout.com`) and **API Key**. Save.

That’s the only “linking”: **your site account (email) → API key → plugin**. No game credentials, no Jagex login, no RuneScape account data.

## Is it compliant?

Yes, for RuneLite and Jagex:

- **No game-account linking** — We never ask for or store RuneScape username, password, or session. The plugin does not log into Jagex on the user’s behalf.
- **No automation** — The plugin only displays data and records GE events. It does not click, move, or automate anything.
- **Transparent** — Users choose to sign up and create an API key. The plugin config clearly shows “Server URL” and “API Key”. Errors are shown in the UI.
- **Secrets** — The API key is stored as a RuneLite secret config field and is not logged or exposed.

So “linking” is only: **your service account (email + API key) ↔ plugin**. Compliant and safe.

## What you should say to users

- “Create an account on our website, then create an API key and paste it into the plugin.”
- “We never ask for your RuneScape or Jagex login. The plugin only uses your API key to fetch market data from our servers.”

## Optional: Discord or social login

If you add “Log in with Discord” or Google, that’s still just **your** account system. The plugin still only needs the API key. No change to RuneLite or Jagex compliance.
