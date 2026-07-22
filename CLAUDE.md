# grand-flip-out plugin (RuneLite) — repo guide

Present-tense rules only. Never pin a PR number or a fork branch name here — those rot within days;
read them live instead (`gh pr list -R runelite/plugin-hub`, `git -C ../plugin-hub status -sb`).

## Build

- **Build oracle is `./gradlew cleanTest test`.** Plain `test` reports BUILD SUCCESSFUL from cache
  ("up-to-date", ~3s) without running anything. Verify a real run via the totals in
  `build/test-results/test/*.xml` (tests/failures/skipped) — never the BUILD line.
- **JDK 11 only.** `~/.gradle/gradle.properties` pins Temurin 11; the host's newer java and any
  JDTLS "Java 25" errors are noise. Gradle is the oracle.

## Shipping to the Plugin Hub

The manifest lives in the plugin-hub FORK (`../plugin-hub`), not here. One PR in flight at a time.

1. Push the plugin commit to this repo's `origin` — **public, so the owner fires every push**.
2. In the fork, set `plugins/grand-flip-out` to two lines —
   `repository=https://github.com/Tunatroll/grand-flip-out.git` and `commit=<full sha>` — on the
   current working branch, commit message `grand-flip-out: update to <sha7>`.
3. Push the fork branch; an open PR's head auto-updates and CI re-runs.

- **Read the live pin before assuming anything:**
  `git -C ../plugin-hub show upstream/master:plugins/grand-flip-out`. A memorised pin is always stale.
- **CI check name is `build`.** A red "RuneLite Plugin Hub Checks" is the normal pre-merge
  maintainer-review signature, not a failure — `build=SUCCESS` is the real check.
- **Retitling a cross-repo PR needs the keyring token** (`env -u GH_TOKEN gh pr edit …`); the
  fine-grained PAT cannot `updatePullRequest` cross-repo.
- **One consolidated pin bump per fully-verified batch**, never incremental dribble.
- Once a Hub PR MERGES, its ship-into-it window is closed — follow-ups need a NEW PR.

## Live numbers (read them, never quote from memory)

- Installs: `curl -s https://api.runelite.net/pluginhub` → JSON map, key `grand-flip-out`.
  This moves by tens per day; any number written down here is wrong by tomorrow.

## Compliance — bright lines for a 3rd-party client plugin

A violation is a rejection, not a review note. No `java.awt.Robot`, synthetic `KeyEvent`s or
`KeyboardFocusManager` input; no auto-confirming an offer; no inserting into the chat input; no
reflection (`setAccessible` / `getDeclaredMethod` / `Proxy` / `Class.forName` — Gson's
`java.lang.reflect.Type` is fine); no JNI; no subprocess/exec; no runtime code download; no
crowdsourcing other players' data; no exposing player info over HTTP; no storing credentials to disk.

`@Inject` the client's Gson, never `new Gson()`. New non-transitive dependencies mean a long review —
keep the build standard. Data-egress disclosure belongs in the Hub manifest `warning=` field, not prose.
