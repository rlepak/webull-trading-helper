# Verification — 2026-11-12

- `./mvnw test`: BUILD SUCCESS; 49 tests, zero failures/errors/skips.
- `openspec validate --all --strict`: five changes passed, zero failed.
- Eight core tests cover batched trade OHLCV/VWAP, decimal precision, exact end
  boundaries, symbol isolation, partial snapshot immutability, idle completion,
  backward/late times, unknown/zero sizes, excluded non-ticks, bounded retention,
  capacity rejection, invalid envelopes and repeated Chicago DST offsets.
- Four service tests cover enable/disable, shared-clock idle completion, identical
  candle output for explicit recorded-time processing, configuration validation and
  actual Spring scheduled execution without another market event.
- Receiver integration verifies quiet ticks reach both recording and aggregation
  with the same receive instant. Existing health and application startup tests
  verify the added summary fields; recorder regression tests continue passing.
- application.properties remains Git-ignored; only the three aggregation settings
  were added there. No extra configuration file, broker calls or dependencies.

Verification is offline with synthetic trades and the existing SDK tests; no new
live Webull run or comparison against chart candles was performed. The replay test
exercises the deterministic aggregation core, not a binary replay runner.

Maven reported a nonfatal certificate-validity warning fetching optional repository
metadata; the build and all tests completed successfully using available dependencies.

Bars use recorded receive time and observed entries, not verified exchange-time
coverage. In-memory history resets on restart; raw binary recording is unchanged.
