# Compliance Checklist (RuneLite + Jagex)

## Hard rules

- No gameplay automation (clicking, movement, combat, skilling, input simulation)
- No bot-like behavior or unfair mechanical advantage features
- No hidden or deceptive network behavior
- No credential leakage in logs/UI

## Plugin behavior constraints

- Analysis and presentation only
- Event listening and local calculations only
- User-triggered UI interactions only
- Hotkeys limited to panel workflow (tab switch/session reset/refresh)

## Security and privacy

- API key uses RuneLite config secret field
- Avoid logging secrets or full auth headers
- Provide clear error/status messages to users

## Submission readiness

- Builds cleanly from source
- Metadata complete (`displayName`, `author`, `description`, `tags`, `plugins`)
- README and docs clear about external API dependency
- Public repo with LICENSE and issue/support instructions

## Review reminders

- Keep feature scope inside policy boundaries
- Document any external service use clearly
- Prefer transparent UX over hidden behavior

## Value protection (compliant)

- Differentiating features (ranked opportunities, live market intelligence) are provided by the grandflipout.com backend, not implemented in the plugin. The plugin is a thin client that displays API responses.
- See **docs/VALUE_PROTECTION.md** for how we keep features defensible without obfuscation or policy violations.

## See also

- **docs/ORIGINALITY_AND_ATTRIBUTION.md** — Code origin (official RuneLite example only; no third-party plugin code).
- **docs/ACCOUNT_LINKING_AND_COMPLIANCE.md** — How users link (email + API key only; no game credentials). Compliant.
- **docs/PAYMENTS.md** — Accepting payment (Stripe) is for your service only; no game or RuneLite policy impact.
