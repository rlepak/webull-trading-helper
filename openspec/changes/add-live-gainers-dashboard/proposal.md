# Proposal

## Why
Identify which of the current top ten gainers are receiving increased trading activity without inspecting raw logs.

## What Changes
- Serve a live table refreshed every second with session ranking, prices, gains and observed ten-second trade volume.
- Highlight a forming window at three times the average of the previous three complete windows; retain the highlight for ten seconds from its first trigger.
- Show connection, freshness and warm-up explicitly. Keep recording disabled.
- No orders, backtests, historical downloads or new broker contracts. No unresolved product decisions for this increment.

## Capabilities
### New Capabilities
- `live-gainers-dashboard`: Session-aware table and observed volume surge signals.
### Modified Capabilities
None.

## Impact
Static assets, read-only HTTP endpoint, bounded in-memory dashboard state and feed integration. Existing Webull selection and aggregation semantics remain intact; no additional dependencies.
