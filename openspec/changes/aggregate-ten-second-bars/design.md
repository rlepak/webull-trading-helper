# Design

## Context

See proposal.md for motivation. MarketDataLogger maps each accepted SDK message to an immutable
event, sends it to the binary recorder, and optionally prints DEBUG JSON. The sibling momentum
and scalping bots align ten-second windows but consume Massive second aggregates. This project
has Webull tick lists, snapshot daily totals and quotes instead.

## Goals / Non-Goals

Keep computation bounded and independent of broker/network/file I/O. Provide application-level
access to immutable completed history and current partial bars, ready for later strategy consumers.
No new endpoint, persistence format, replay runner or trading decisions in this change.

## Decisions

- Use only TickData with matching TICK type and valid symbol/receive instant. Every valid-price
  trade in a message contributes in list order. Order open/close by receive instant, preserving
  ingestion order for equal timestamps. Trade time strings have no date and provider timestamp
  semantics are unverified; do not guess exchange time or reuse daily snapshot OHLC/volume.
- Pure synchronized TenSecondBarAggregator uses epoch-aligned [start,end) windows, immutable
  OffsetDateTime output, BigDecimal arithmetic and DECIMAL128 VWAP. Time basis is explicit.
  A monotonic bucket boundary closes old bars. accept(event) advances to that event's boundary;
  advanceTo(instant) supports timer and future replay. Backward timestamps in an open window
  update extrema/volume but use timestamp ordering for open/close; closed-window trades count late.
- Spring service schedules advancement every second and delegates accepted events to the same
  core. Timestamp capture, raw recording and aggregation share the service monitor with timer
  advancement so a boundary timer cannot overtake an already timestamped live callback.
  Current and completed getters return immutable snapshots. No per-bar console output.
- Existing application.properties gains enabled=true, history-per-symbol=360 and max-symbols=100.
  Validate positive bounded capacities (up to 3600 bars and 1000 symbols). Retain one partial bar
  per symbol and evict oldest completed bars. Evict least-recently-traded idle symbol on churn;
  reject new symbols if all slots have partial bars. Counters make rejection observable.
- Unknown or negative sizes make total volume/VWAP null (volumeComplete=false); zero size is
  valid, but zero total volume gives null VWAP. Skip nonpositive/null prices with an invalid counter.
- Extend existing health summary with enabled state, active bars, completed total, invalid/late/
  capacity trade counters. No scheduling changes to health logs; raw binary format unchanged.

## Risks / Trade-offs

- Receive-time bars differ from exchange candles, and subscriptions/reconnects may miss trades
  → label timing explicitly; completed means time elapsed, never complete exchange coverage.
- Webull ticks have no unique trade identifier in our model → count observed entries, do not
  deduplicate identical price/size/time tuples that may represent legitimate repeated trades.
- Wall clock rollback or callback arriving after timer finalization → monotonic boundary rejects
  late trades observably; replay must use recorded instants and explicit advancement, never machine time.
- Restart loses in-memory history and unfinished bars → raw recordings remain the source for later
  reconstruction. Shutdown does not falsely complete a still-open interval.

## Migration Plan

Restart after deployment; new aggregation is enabled in the single existing configuration file.
Set aggregation.enabled=false to disable it without changing streaming or recording. No dependency
or broker entitlement changes and no binary data migration.
