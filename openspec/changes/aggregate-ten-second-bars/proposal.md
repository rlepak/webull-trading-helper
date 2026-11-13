# Proposal

## Why

The helper records trades but has no short-interval candles for subsequent analysis.
Add the 10-second aggregation used by the sibling trading bots, adapted to Webull tick input.

## What Changes

- Build per-symbol 10-second OHLC, volume, trade count and VWAP from accepted ticks.
- Align windows to clock boundaries and expose immutable current and completed bars to application code.
- Use recorded receive instants; display boundaries with Chicago offsets. Finalize elapsed windows even when trading stops.
- Keep bounded memory and add aggregation counters to existing health reports, without printing every candle.
- Retain raw binary records unchanged. A deterministic core accepts explicit times for future replay.

Scope assumes “10 aggregation” means ten seconds, matching both sibling projects.
Exchange-time reconstruction, duplicate trade identification without provider IDs, bar files,
HTTP/UI endpoints, strategies, orders and a replay runner are outside this increment.

## Capabilities

### New Capabilities
- `trade-bar-aggregation`: Receive-time trade candles with bounded retention and observable rejection counts.

### Modified Capabilities
None.

## Impact

New aggregation package, accepted-event dispatch and health reporting; configuration stays in
application.properties. No dependencies or additional Webull requests. The sibling bots use
Massive second aggregates, so their input contracts cannot be copied directly.
