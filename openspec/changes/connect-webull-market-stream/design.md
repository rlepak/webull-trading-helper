# Design

## Context

See proposal.md. Existing sibling projects use Webull Java SDK 1.1.20. Its source confirms IDataStreamingClient, MarketStreamingClient, and Quote/Snapshot/Tick payloads. The current project is a Java 21 Spring Boot scaffold. Official docs describe MQTT v3.1.1 over TLS, port 1883, with HTTP subscription management.

## Goals / Non-Goals

**Goals:** Separate transport, mapping, and logging so aggregation can consume immutable data later. Keep credential configuration in the single existing properties file.

**Non-Goals:** No aggregation, database, REST market-data facade, account operations, order execution, historical retrieval, or additional config files.

## Decisions

- Add javax.xml.bind:jaxb-api:2.3.1 because the SDK signing path uses DatatypeConverter, absent in Java 21. A real SDK signing regression test covers this runtime dependency.
- Pin the existing Webull SDK version, using its MQTT transport and protobuf decoders instead of reimplementing signed HTTP and wire formats. Use AAPL as a configurable initial smoke symbol and US_STOCK as the supported initial category.
- Use an application-owned lifecycle worker with SDK reconnection disabled. Each session registers its consumer before connecting, subscribes separately per type, waits for disconnect, then cleans up before retrying. This exposes each subscription result and avoids competing retry loops. Backoff grows from 2 to 60 seconds; shutdown interrupts the worker.
- Configure SDK HTTP timeouts and disable HTTP retries; report sanitized exception categories/statuses rather than exception messages or bodies. SDK request logging stays off.
- Map SDK payloads to Java records (immutable POJOs), using BigDecimal for decimals and preserving raw provider timestamps. Snapshot fields are retained, including extended-hours values. Quote depth defaults to one; tick batches retain each trade.
- Log mapped events as single-line JSON through a dedicated consumer, allowing a future aggregation consumer without changing transport. Unknown/malformed events produce sanitized diagnostics. No claim of lossless durable capture is made.
- Tests use SDK objects and mock clients; no live network or real credentials are required. A bounded manual live smoke check records actual availability separately.

## Risks / Trade-offs

- Data entitlement is unknown → log denied subscriptions accurately; never purchase services.
- SDK's protobuf decoder can reject a bad wire message before our mapper → treat stream errors as session failures and reconnect.
- Per-event INFO logging adds load → keep symbol subscriptions bounded and make logging independently configurable.
- Local properties enter build output → keep outputs ignored and do not distribute credential-bearing builds.
- Timestamp units vary → preserve original fields instead of guessing conversions before aggregation requirements exist.

## Migration Plan

Run tests, enable the configured stream, and inspect connection/subscription/event logs. Disable webull.streaming.enabled to roll back the connection. No persisted schema changes.

## References

- https://developer.webull.com/apis/docs/market-data-api/getting-started/
- https://developer.webull.com/apis/docs/market-data-api/data-streaming-api/
- Local SDK 1.1.20 source JAR (inspected interfaces and payload models).
