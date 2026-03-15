# How to submit Grand Flip Out to the RuneLite Plugin Hub

This is a plain-language walkthrough so you can submit the plugin like a human would: one step at a time, with room to fix things before you hit submit.

---

## Before you start

- **Your plugin builds and runs.** You've run `./gradlew run`, opened the panel, and maybe done a test trade or two.
- **Your API is live.** If you're using grandflipout.com (or your own URL), it's deployed and returning market/opportunities so reviewers and users aren't hitting a dead endpoint.
- **You've read the rules.** RuneLite and Jagex have clear policies (no automation, transparent behavior, etc.). We're already aligned; double-check their current hub/submission page so nothing's changed.

---

## Step 1: Put your name (or team name) on it

- Open **runelite-plugin.properties**.
- Set **author** to whatever you want on the hub (your username, name, or a team name).
- Make sure **displayName**, **description**, and **tags** still say what the plugin does in a way that's clear to a random player.

---

## Step 2: Add at least one screenshot

- Run the plugin in RuneLite (`./gradlew run`).
- Open the Grand Flip Out panel and the tabs (Overview, Live Market, Trade History).
- Take a screenshot (or two) that shows the plugin in action—e.g. the panel with some numbers or the Live Market list.
- Put the image(s) in the repo (e.g. in the README or a `screenshots/` folder) and reference them in the README so the hub listing can look like a real product.

---

## Step 3: Do a quick manual pass

- Use **docs/QA_MANUAL_TEST.md** as a checklist.
- On a clean profile (or fresh install), enable the plugin, set your API key, do a couple of GE trades, and confirm the panel updates and nothing crashes.
- If anything's broken, fix it before submitting. Reviewers and users will see the same thing.

---

## Step 4: Submit through RuneLite's process

- Go to the official RuneLite plugin hub / submission page (check the RuneLite docs or GitHub for the current link).
- You'll likely need to point them at your repo (public GitHub is typical), confirm the plugin is open source, and that it follows their policy.
- Fill out the form as yourself: describe what the plugin does, that it uses an external API for market data, and that users need to sign up for an API key. No need to sound like a robot—just clear and honest.
- If they ask about code origin: the plugin was started from the official RuneLite plugin example; all application code is original (see [Originality and attribution](ORIGINALITY_AND_ATTRIBUTION.md)).
- Submit. Then wait for feedback. If they ask for changes (e.g. wording, one more screenshot, or a config tweak), make the edits and reply. It's a human on the other side.

---

## If something's unclear

- RuneLite's own docs and Discord (if they have one) are the best place for hub-specific questions.
- In this repo, **docs/COMPLIANCE_CHECKLIST.md** and **docs/SUBMISSION_CHECKLIST.md** are there to make sure we've covered policy and metadata before you submit.

You've built something that respects the rules and helps people flip with better data. Submitting it "as human as possible" just means: put your name on it, show it with a screenshot, test it once, and describe it honestly. Then hit submit.
