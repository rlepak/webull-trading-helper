# Design

## Context
Existing selection, receive-time aggregation and MQTT lifecycle provide the data. See proposal.md. No new broker calls or dependencies are needed.

## Goals / Non-Goals
Expose a bounded, read-only view of existing data; no execution or strategy logic.

## Decisions
- Keep provider ranking rows alongside selected symbols, preserving ranking order even when membership is unchanged. Label gain as the latest ranking value rather than manufacturing it from a tick and an unknown reference close.
- Reuse existing ten-second bars. Require consecutive known-volume bars after the first partial subscription window; missing bars are unknown rather than synthetic zeros. This is conservative for sparse stocks.
- Publish accepted immutable market events internally after aggregation. Dashboard tracks snapshot values, event freshness and surge expiry, bounded to selected symbols. Polling the page does not drive detection; events and a one-second timer maintain state.
- Session/date and stream connection generation reset state. Only display ranking fetched for the current session/date. Extended snapshot fields require PRE/ATH matching the existing mapper conventions; unknown/mismatched sessions do not replace values.
- Static HTML/CSS/JS with a same-origin JSON endpoint; no build pipeline or external CDN. Escape content using textContent. Fetches do not overlap and have a timeout.
- Configure threshold, baseline bars, highlight seconds and stale time in the existing ignored application.properties, with validated Java defaults.

## Risks / Trade-offs
Observed volume is not guaranteed consolidated exchange volume; label it explicitly. Receive-time bins differ from exchange-time charts. Ranking gain updates on the existing polling interval, independent of live price updates. This increment exposes no credentials or orders, and is intended for the existing trusted local/LAN deployment.

## Migration Plan
Restart the application and open /. Raw recording remains disabled. Roll back the dashboard changes to remove the page without touching stored data.

Highlight expiry uses an exact Instant ten seconds after first detection. Track the last triggered window so polling and subsequent trades in the same window cannot extend expiry. A new window can independently trigger a new highlight.

Average trade size is derived in the browser from the same API row currentVolume and tradeCount; no additional broker fields, configuration, or API changes are required. The column is labeled shares per trade for the forming 10-second window.
