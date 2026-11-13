# Proposal

## Why

REQ-001 needs a working market-data input before aggregation can be built. The user explicitly requests Maven dependencies, a socket client, POJOs, and logging of received data.

## What Changes

- Connect to Webull's TLS MQTT market stream using the official SDK and existing local credentials.
- Subscribe to configurable US-stock symbols and QUOTE, SNAPSHOT, TICK data; default smoke symbol is AAPL.
- Convert incoming SDK objects into immutable application data objects and log each accepted event.
- Add bounded reconnect delays, isolated subscription errors, shutdown cleanup, and offline tests.
- Keep all application settings in application.properties as requested.

## Capabilities

### New Capabilities

- `webull-market-stream`: Configurable read-only streaming connection and typed event logging.

### Modified Capabilities

None.

## Impact

Adds Webull SDK 1.1.20, Spring lifecycle/configuration components, typed event models, and tests. Uses existing free entitlements without purchasing services. Account reads, historical pulls, aggregation, persistence, UI, and order execution are outside this implementation. Symbol list is configurable; AAPL and US_STOCK are initial defaults, not trading recommendations. Actual entitlement and live data receipt must be verified separately from offline tests.
