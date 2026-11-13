# Design

## Context

See proposal.md. Existing transport owns one SDK MQTT session and subscribes to a fixed symbol list. The installed SDK 1.1.20 gainers call was verified live on 2026-11-12: 200 rows, prices and change ratios populated. Its legacy response reported hasMore=true; the requested bounded ranking remains the first 200 entries, not a crawl of all pages.

## Goals / Non-Goals

Goals: Discovery and subscription state are separate, testable, and thread-safe. Normal polls run at five-second intervals, and slow calls never overlap or trigger catch-up bursts. Keep successful subscriptions across membership changes.

Non-goals: Exchange-calendar interpretation, trading, custom ranking algorithms, all-market pagination, or replacing typed event logging.

## Decisions

- Dedicated TopGainersProperties under webull.top-gainers holds enabled, poll interval, timezone, min/max price. All local settings remain in application.properties. Discovery starts empty; disabled mode retains the existing manual symbol setting.
- A Clock-based session selector uses Chicago LocalTime. PRE_MARKET and AFTER_MARKET are chosen exactly as requested, including overnight/weekends. DAY_1 represents regular-session daily gainers.
- A reusable SDK DataClient performs a single getGainersLosers(rank, US_STOCK, CHANGE_RATIO, 1, 200, DESC) call per refresh. Map rows to small application records and validate before selecting. A null list is a failure, while an empty list is valid.
- A single scheduled worker schedules its next call after each completion, subtracting elapsed time from the five-second interval and never catching up. Errors use increasing bounded delays; 429 starts at 60 seconds. SDK HTTP retries are disabled. SDK exceptions do not expose response headers, so Retry-After is unavailable through this interface.
- A shared selection component holds an immutable desired symbol snapshot. Successful polls replace it; a live out-of-range price removes a symbol immediately. Invalid/missing prices never become zeros. Existing raw provider timestamps remain unchanged.
- The existing socket worker periodically reconciles per-type confirmed subscriptions against desired symbols. Remove before adding; batch at most 50 symbols per call, pace changes, and keep per-type retry deadlines. Only confirmed success changes state. No socket replacement for list changes.
- SDK ApiSubscriptionManager removal does not consistently match its stored depth key. Avoid its local subscription cache for dynamic operations: expose the signed MarketStreamingClient alongside the MQTT client and manage subscriptions directly using the same session ID. SDK auto-reconnect remains disabled; our reconnect reconstructs from desired symbols.
- Snapshot extended-hours prices are used when explicitly identified by the current stream session; otherwise use the current snapshot price. Trade messages use their last trade price. Quote bid/ask changes do not establish a traded-price boundary crossing.

## Risks / Trade-offs

- Other apps share API quota → five-second polls and throttling backoff do not guarantee no 429.
- Stale or failed discovery → preserve last good list and log failures; live price exits still apply.
- Large membership changes → paced batches converge rather than bursting; removed-symbol events are suppressed locally while removal completes.
- Feed updates race polling → synchronize updates; later successful screener observations may requalify symbols.
- Live verification only covers the current market session → deterministic tests cover all time boundaries and DST.

## Migration Plan

Enable webull.top-gainers.enabled locally. Preserve PLCE manual fallback for disabled discovery. No dependencies or additional configuration files are required. Roll back by disabling discovery.

### Verified subscription ceiling

Webull live verification returned HTTP 417, `TOO_MANY_SYMBOLS_SUBSCRIPTION`, with a maximum of 100 subscribed tickers. The requested selection now uses the highest-ranked 10 eligible stocks from the first 200 ranking entries. Filtering happens before this cap.

## Shared time convention

Use app.time-zone=America/Chicago in the existing application.properties. Session zone
and console date formatting refer to it. Inject the shared zoned Clock into receive
logging; retain Instant in the event model and render receivedAt with the Chicago
offset in JSON. Preserve broker-provided timestamps verbatim. Future binary recording
and replay will use Chicago for session/date/hour paths and exact instants for ordering.
