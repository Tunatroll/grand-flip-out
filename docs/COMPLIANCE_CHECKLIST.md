# Compliance & submission checklist

Quick reference so we stay within the rules and look ready when you submit. Use with [SUBMISSION_CHECKLIST.md](SUBMISSION_CHECKLIST.md) and [QA_MANUAL_TEST.md](QA_MANUAL_TEST.md).

---

## Policy (we're good)

- **No automation** — The plugin doesn't click, move, or simulate input. It only shows data and records GE events.
- **Transparent** — We document the API; the user sets the URL and key. Errors show in the panel.
- **Secrets safe** — The API key is stored as a RuneLite secret and isn't logged or shown in the clear.

---

## Hub readiness

- **Done:** Metadata in `runelite-plugin.properties`, README, LICENSE, build passes, no template leftovers. **Original work:** All plugin code is ours; started from the official RuneLite example only. See [Originality and attribution](ORIGINALITY_AND_ATTRIBUTION.md).
- **You do before submit:** Put your name (or team) in `author`, add at least one screenshot, run through the QA doc once, and make sure your API is up if others will use it.

---

## When you're ready

1. Run [QA_MANUAL_TEST.md](QA_MANUAL_TEST.md) on a clean profile.
2. Confirm the API is live and returns data.
3. Update author and add a screenshot.
4. Follow [HOW_TO_SUBMIT.md](HOW_TO_SUBMIT.md) and RuneLite's current submission process.

No surprises—just the usual human steps before you hit submit. For a single list of everything only you can do (author, screenshot, deploy, Discord, Stripe), see [BEFORE_RELEASE.md](BEFORE_RELEASE.md).
