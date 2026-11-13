# Verification

Date: 2026-11-12.

- Maven: `./mvnw -q test` passed all 24 tests, no failures/errors/skips.
- OpenSpec: `openspec validate --all --strict` passed both active changes.
- Tests cover Chicago session boundaries, DST, weekends, inclusive price bounds,
  invalid rows, positive gains, the 100-symbol selection ceiling, failed/empty
  discovery, 429 backoff, live price exits, subscription deltas, paced batching,
  pending failed removals, and reconnecting with the latest selection.
- Initial read-only live discovery returned 200 ranking entries via SDK 1.1.20.
- Initial integrated stream received 1,574 events. Attempting more than 100 unique
  subscriptions failed. A separate bounded probe confirmed HTTP 417,
  `TOO_MANY_SYMBOLS_SUBSCRIPTION`, maximum 100 tickers. Selection now filters
  prices first and caps at the highest-ranked 100 eligible entries.
- Only the regular-market DAY_1 ranking was verified live; premarket/aftermarket
  scheduling is covered by deterministic tests. Holidays follow the requested
  wall-clock schedule.
- Five-second polling is not a guarantee against rate limiting, especially if
  other applications share credentials. Discovery retains its previous selection
  on failure and backs off for at least 60 seconds on HTTP 429.
- Raw local verification logs are in ignored target/; no credentials are recorded
  in this document. No trades or subscription purchases were made.

## Final live check

The capped implementation polled eight times at approximately five-second intervals, selecting 100 stocks each time. All initial QUOTE, SNAPSHOT, and TICK batches succeeded. GIFT was removed across all three types and CLNE added across all three on the same socket as ranking membership changed. Received 1,282 events (QUOTE=228, SNAPSHOT=299, TICK=755), covering 39 active symbols during the bounded observation. No subscription errors or HTTP 429 appeared. The final shutdown interrupted a subsequent membership update as expected; the socket closed. Raw output: `target/top-gainers-smoke-final.log`.

## Top-ten revision

The user subsequently limited selection to the highest-ranked 10 eligible stocks for whichever session is active. Filtering remains before truncation. Updated regression coverage confirms that five out-of-range leading stocks are skipped, the next ten are selected, and the eleventh eligible stock is excluded. Existing tests cover fewer qualifying stocks and empty results. `./mvnw test` passed 24 tests; `openspec validate --all --strict` passed both changes. This revision was verified offline; the earlier 100-ticker live check above is historical evidence, not a live test of the new cap.

## Chicago timezone revision

Configured app.time-zone=America/Chicago in the sole application.properties and
referenced it for session selection and console date formatting. The shared Clock
uses this zone. JSON receivedAt displays the Chicago offset while the event model
retains its exact Instant and original provider fields. Winter and summer receive
time assertions pass, including nanosecond precision in winter.
Maven: 25 tests passed, no failures/errors/skips. Strict OpenSpec validation:
both active changes passed. Binary recording and replay remain planned work.
