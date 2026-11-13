# Proposal

## Why

The user wants the helper to follow affordable top gainers automatically throughout premarket, regular hours, and aftermarket instead of a fixed symbol list.

## What Changes

- Select PRE_MARKET at 03:00–08:30, DAY_1 at 08:30–15:00, and AFTER_MARKET otherwise, using America/Chicago wall time and its daylight-saving rules every day.
- Poll gainers every five seconds without overlapping requests; back off after throttling or errors.
- Select returned stocks priced inclusively between $0.10 and $10, excluding missing/invalid prices and nonpositive gains.
- Reconcile added and removed symbols on the existing live stream, preserving unchanged subscriptions and restoring the latest selection after reconnect.
- Remove a subscribed stock when a received trade/snapshot price leaves the allowed range; reassess on subsequent polls.
- Keep settings solely in application.properties; no order execution, paid subscriptions, market calendar overrides, or aggregation.

## Capabilities

### New Capabilities

- `session-top-gainers`: Scheduled gainers discovery, price eligibility, and dynamic stream selection.

### Modified Capabilities

None in the main spec inventory. This builds on the verified, unarchived webull-market-stream change; manual symbol mode remains available when discovery is disabled.

## Impact

Adds a read-only screener gateway, session selector, polling lifecycle and dynamic subscription reconciliation. Existing MQTT transport, typed events, and credential handling remain in use. The current manual PLCE setting is preserved as the disabled-discovery fallback. Filter the bounded provider ranking first, then select at most 10 eligible stocks for the active session.

Webull live verification returned HTTP 417, `TOO_MANY_SYMBOLS_SUBSCRIPTION`, with a maximum of 100 subscribed tickers. The requested selection now uses the highest-ranked 10 eligible stocks from the first 200 ranking entries. Filtering happens before this cap.

Application-generated timestamps and the shared clock use America/Chicago consistently; provider timestamps retain their original representation.
