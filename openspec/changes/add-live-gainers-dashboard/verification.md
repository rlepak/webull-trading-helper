# Verification — 2026-11-18

- Java 21, `./mvnw test`: **60 tests passed**, zero failures/errors/skips.
- New coverage: exact 3× threshold, below threshold, initial partial window,
  missing/unknown/zero baseline, stale current bar, hold expiry, stale rows and
  ranking, disconnected/reconnected feed, rapid selection reentry, session/date
  changes, PRE/RTH/ATH snapshot fields, ranking reorder and no-store response.
- `openspec validate add-live-gainers-dashboard --strict`: passed.
- `openspec validate --all --strict`: six changes passed; five unrelated leftover
  strategy changes fail because they have no deltas: backtest-premarket-breakout,
  delay-premarket-entries, filter-dynamic-premarket-gainers,
  filter-premarket-volume and require-percent-breakout. These existing folders
  were left untouched; whole-project validation is not claimed as passing.
- `node --check src/main/resources/static/dashboard.js` and `git diff --check`: passed.
- Actual Spring Boot preview on 127.0.0.1:18080 with streaming and binary recording
  explicitly disabled: GET / and /api/dashboard worked; JSON returned disconnected
  state, empty rows and Cache-Control: no-store, without credentials.
- Headless installed Chrome / Playwright: verified offline empty state, two fixture
  rows with one green 3.6× surge, unknown volume, 390px mobile horizontal table
  containment, HTTP 503 retry message and removal of green highlights; no JS errors.
  Desktop and mobile screenshots inspected. Fixture data stayed in the browser
  test; no sample prices are shipped in the application.
- No live broker behavior or production deployment was tested in this change.
- Local application.properties retains disabled recording and has the four
  dashboard settings; obsolete strategy fields remain absent. Credentials were
  not printed, copied or changed.

## Ten-second highlight follow-up

Replaced window-relative hold with `dashboard.highlight-seconds=10`. Repeated
updates in the triggering window do not extend expiry. The regression test
checks visibility at +9 seconds and expiry at +10 seconds after additional
trades and snapshot requests. All 60 Maven tests pass. Strict validation passes
for this change; the same five unrelated incomplete changes still fail globally.
