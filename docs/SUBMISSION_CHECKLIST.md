# RuneLite submission checklist

Use this before you submit so nothing obvious is missing. Tick as you go.

---

## Already done

- **Metadata** — displayName, author, description, tags, and plugins are set in `runelite-plugin.properties`. Main class and package are ours, not the template.
- **README** — Explains what the plugin does, that it uses an external API, and how to get an API key. LICENSE is in the repo.
- **Build** — `./gradlew compileJava compileTestJava` passes. No automation; API key is a secret config field; errors show up in the UI.
- **UX** — Side panel with Overview, Live Market, Trade History. Hotkeys, persistence, and export/import are there.
- **Original work** — No code from other plugins; started from official RuneLite example only. See [Originality and attribution](ORIGINALITY_AND_ATTRIBUTION.md).

---

## Your to-do before you submit

1. **Put your name on it** — In `runelite-plugin.properties`, set `author` to whatever you want on the hub (your name, username, or team name).
2. **Add a screenshot** — Run the plugin, open the panel, take a screenshot. Add it to the README or a `screenshots/` folder so the listing doesn’t look bare.
3. **Quick test run** — Go through [docs/QA_MANUAL_TEST.md](QA_MANUAL_TEST.md) once on a clean profile. Fix anything that’s broken.
4. **API is live** — If people will hit your API (e.g. grandflipout.com), make sure it’s deployed and stable so reviewers and users don’t get errors.

Then submit via RuneLite’s process. 5. **Optional:** Replace Discord invite in website (see [DISCORD.md](DISCORD.md)); set up Stripe ([PAYMENTS.md](PAYMENTS.md)) if you want payments.

[How to submit (plain steps)](HOW_TO_SUBMIT.md) is in this folder if you want a walkthrough.
