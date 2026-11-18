# Requirements

This is the ordered backlog for Webull Trading Helper. We will define and
implement requirements one at a time using OpenSpec.

This file tracks priority and progress. Detailed behavior and acceptance scenarios
belong in the linked OpenSpec change; completed capability specs live in
`openspec/specs/`. Keep links and status here current without duplicating those specs.

## Confirmed project direction

- Build a Webull trading helper using the existing Java 21 / Spring Boot project.
- Use Webull data available without purchasing additional data subscriptions.
- First establish the Webull connection and retrieve useful available data.
- Use OpenSpec to specify changes before implementation.
- Use America/Chicago for application time display and session/date/hour boundaries;
  preserve exact event instants and original provider timestamps.
- Implement one requirement at a time unless the user requests otherwise.

## Decisions for later requirements

Historical retrieval, account data and other instrument types
remain future work. REQ-001 now covers the requested socket connection,
typed market events, and logging. Initial US-stock symbols are configurable.

## Ordered backlog

Assign subsequent IDs in sequence and keep IDs stable when reordering.

| Order | ID | Requirement | Status | Depends on | OpenSpec change |
| --- | --- | --- | --- | --- | --- |
| 1 | REQ-001 | Connect to Webull and retrieve available free data | Verified | None | [connect-webull-market-stream](openspec/changes/connect-webull-market-stream/proposal.md) |
| 2 | REQ-002 | Follow session top gainers priced $0.10–$10 | Verified | REQ-001 | [select-session-top-gainers](openspec/changes/select-session-top-gainers/proposal.md) |
| 3 | REQ-003 | Record hourly binary market data | Verified | REQ-002 | [record-binary-market-data](openspec/changes/record-binary-market-data/proposal.md) |
| 4 | REQ-004 | Report health every five minutes with quiet console | Verified | REQ-003 | [periodic-health-logging](openspec/changes/periodic-health-logging/proposal.md) |
| 5 | REQ-005 | Aggregate observed trades into 10-second candles | Verified | REQ-004 | [aggregate-ten-second-bars](openspec/changes/aggregate-ten-second-bars/proposal.md) |
| 6 | REQ-006 | Live session top-ten gainers and volume surge table | Verified | REQ-002, REQ-005 | [add-live-gainers-dashboard](openspec/changes/add-live-gainers-dashboard/proposal.md) |

## Requirement entries

### REQ-001 — Connect to Webull and retrieve available free data

- Status: Verified
- User need: Receive Webull market data now so aggregation can be added later.
- Confirmed implementation: Maven dependencies, a socket client, immutable POJOs,
  and logging of received events.
- Scope: TLS MQTT market stream with configurable US-stock symbols, QUOTE,
  SNAPSHOT, and TICK subscriptions, precise typed payloads, provider and receive
  timestamps, sanitized diagnostics, reconnect/backoff, and shutdown cleanup.
- Initial defaults: AAPL, depth 1, no overnight subscription; configurable stream
  types and symbol list in the existing application.properties.
- Configuration: Only local application.properties; no example/profile files.
  Existing credentials are preserved and the file is excluded from Git.
- Out of scope: Account reads, history downloads, aggregation, UI, order
  execution, and subscription purchases.
- Acceptance criteria: See the [stream spec](openspec/changes/connect-webull-market-stream/specs/webull-market-stream/spec.md).
- Dependencies: Existing credentials and the account's actual OpenAPI data access.
- OpenSpec change: [connect-webull-market-stream](openspec/changes/connect-webull-market-stream/proposal.md).
- Verification: 13 offline tests passed; live MQTT connected and all three AAPL
  subscriptions were accepted. Received 161 events (QUOTE=73, SNAPSHOT=31, TICK=57).
  See [verification evidence](openspec/changes/connect-webull-market-stream/verification.md).
  Implementation is verified; OpenSpec archive remains a separate follow-up.

#### Existing integration evidence

Inspected the three requested sibling projects:

- `premarket-momentum-trader`: no Webull references found in the searched project
  files (excluding build outputs, logs, IDE metadata, and dependency directories).
- `momentum-trading-bot`: Webull Java SDK 1.1.20; quote reads via `DataClient`.
- `scalping-trading-bot`: Webull Java SDK 1.1.20; read-only account discovery and
  balance reads with request pacing and backoff.

The latter two projects have matching app key, app secret, region, and endpoint
settings; both leave the account ID blank. No credential values belong here.
Local SDK source inspection confirms methods for the candidate data categories,
but does not confirm live availability or entitlement.

References: [Webull Data API](https://developer.webull.com/apis/docs/market-data-api/data-api/)
and [OpenAPI subscriptions](https://developer.webull.com/apis/docs/market-data-api/subscribe-quotes/).
Webull documents separate OpenAPI subscriptions for advanced data; free availability
must be established for the user's account rather than inferred from the mobile app.

### REQ-002 — Follow session top gainers priced $0.10–$10

- Status: Verified
- Scope: Poll top gainers every five seconds; use PRE_MARKET from 03:00–08:30,
  DAY_1 from 08:30–15:00, and AFTER_MARKET otherwise in America/Chicago.
- Eligibility: Positive gain, valid symbol and price in the inclusive $0.10–$10.00
  range among the first 200 returned ranking entries, capped at the highest-ranked
  10 eligible stocks for the active session. Exclude unknown prices.
- Stream behavior: Add/remove only changed symbols on the existing socket; remove
  stocks whose observed trade/snapshot price exits the range. Reconnect restores
  the latest selection. A failed fetch preserves the previous list; successful
  empty results clear it. Back off after 429 instead of repeatedly retrying.
- Configuration: Only application.properties; preserve manual symbol fallback
  when discovery is disabled. Apply the wall-clock schedule every day.
- Acceptance criteria: [Session top-gainers spec](openspec/changes/select-session-top-gainers/specs/session-top-gainers/spec.md).
- Verification: The top-10 revision passed all 24 tests and strict OpenSpec validation. Prior live discovery
  and all three data subscriptions succeeded; 1,282 events received and ranking
  membership changes reconciled on the same socket. See
  [verification evidence](openspec/changes/select-session-top-gainers/verification.md).

### REQ-003 — Record hourly binary market data

- Status: Verified
- Scope: Accepted quotes, snapshots and ticks saved in Chicago session/date/hour folders, using versioned length-prefixed records and safe durable append.
- Configuration: Existing application.properties only.
- Acceptance criteria: [Binary recording spec](openspec/changes/record-binary-market-data/specs/binary-market-recording/spec.md).
- OpenSpec change: [record-binary-market-data](openspec/changes/record-binary-market-data/proposal.md).
- Verification: 32 tests and strict spec validation passed; a live check captured
  and decoded 437 ordered records. See [evidence](openspec/changes/record-binary-market-data/verification.md).
- Follow-up requested: Offline replay/backtesting; this increment implements the writer.

### REQ-004 — Five-minute health logging

- Status: Verified
- Scope: Quiet routine console output, a health summary at startup and every five minutes, immediate warnings/errors; continuous binary capture.
- OpenSpec change: [periodic-health-logging](openspec/changes/periodic-health-logging/proposal.md).
- Verification: 36 tests, strict spec validation, and an offline scheduler smoke check passed.
  See [evidence](openspec/changes/periodic-health-logging/verification.md).

### REQ-005 — Ten-second trade aggregation

- Status: Verified
- Scope: Receive-time OHLCV, VWAP and trade count per symbol, partial/completed bars,
  bounded in-memory history, idle finalization and health counters. Chicago display.
- Acceptance criteria: [Trade bar spec](openspec/changes/aggregate-ten-second-bars/specs/trade-bar-aggregation/spec.md).
- OpenSpec change: [aggregate-ten-second-bars](openspec/changes/aggregate-ten-second-bars/proposal.md).
- Raw binary recordings stay unchanged; replay runner and exchange-time candles remain follow-up work.
- Verification: 49 tests and strict validation of all five changes passed, including
  idle scheduler execution. See [evidence](openspec/changes/aggregate-ten-second-bars/verification.md).

### REQ-006 — Live gainers dashboard

- Status: Verified (offline tests and browser checks).
- Scope: Session top-ten table, one-second refresh, observed ten-second volume
  surge highlighting, average shares per trade, explicit freshness and warm-up. No orders or raw recording.
- OpenSpec change: [add-live-gainers-dashboard](openspec/changes/add-live-gainers-dashboard/proposal.md).
- Verification: 60 tests passed; dashboard spec passed strict validation. Browser
  checks used offline fixture data. Full-project validation still flags five
  unrelated reverted strategy change folders. See [evidence](openspec/changes/add-live-gainers-dashboard/verification.md).

Statuses: **Draft** → **Specified** → **In progress** → **Verified** → **Done**.
Use **Blocked** when progress requires an unresolved decision or dependency, and
record the reason in the requirement entry.

## Working through a requirement

1. Add its description using the template below and put it in the backlog.
2. Resolve material ambiguity, then use `openspec-propose` to generate its artifacts.
   Link the change here and mark the requirement **Specified** when the artifacts
   define its scope and acceptance criteria.
3. When implementation is requested, use `openspec-apply-change` and mark it
   **In progress**. Finish the current requirement before starting the next.
4. Check its acceptance scenarios and appropriate tests. Record evidence in the
   change and mark it **Verified** only when those checks pass.
5. Archive the completed change, update the link to its archive location, and mark
   the requirement **Done**. Select the next item whose dependencies are satisfied.

A request to add or refine requirements does not by itself request implementation.

## Requirement entry template

Copy this block for each new requirement; replace placeholders before specifying it.

```markdown
### REQ-NNN — Short title

- Status: Draft
- User need: Who needs this, and what problem does it solve?
- Expected outcome: What observable result should the user get?
- Scope: What is included?
- Out of scope: What is excluded?
- Acceptance criteria:
  - Observable success condition.
  - Relevant failure or boundary condition.
- Dependencies: None, or other requirement IDs.
- Open questions: None, or decisions needed before specifying.
- OpenSpec change: Add a relative link when created.
- Verification: Add evidence or a link when verified.
```

Webull live verification returned HTTP 417, `TOO_MANY_SYMBOLS_SUBSCRIPTION`, with a maximum of 100 subscribed tickers. The requested selection now uses the highest-ranked 10 eligible stocks from the first 200 ranking entries. Filtering happens before this cap.
