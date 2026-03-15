# Sensitive Content and Repo Safety

## What must never be committed

- **API keys, passwords, tokens** — The plugin stores the user's API key in RuneLite config (local only). Do not hardcode keys in source.
- **`.env` files** — Backend and website env vars (DB URLs, secrets, Stripe keys). Already in `.gitignore`.
- **Secrets directories** — `secrets/`, `*.pem` (certs/keys). Already in `.gitignore`.

## What is safe in the repo

- **Docs (ROADMAP, API_CONTRACT, BACKEND_AUTH_SPEC, etc.)** — Describe product direction and API shape; no credentials.
- **Plugin config keys** — Names like `apiBaseUrl`, `apiKey` are config item names; values are entered by the user and stored by RuneLite locally.
- **Website placeholder pages** — Login/signup/dashboard are static placeholders; real auth is implemented on your backend and not stored in the repo.

## If you ever pushed by mistake

- Rotate any exposed keys or passwords immediately.
- Remove the secret from history (e.g. `git filter-branch` or BFG) and force-push only if you understand the impact; otherwise revoke and regenerate the secret and do not push again.

## Working locally

- All development in this workflow is local. No automated git push or pull requests. When you are ready to publish, you control when and what to push.
