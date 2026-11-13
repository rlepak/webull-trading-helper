# Verification

- Date: 2026-11-10.
- `./mvnw test`: 13 tests passed, zero failures/errors.
- `openspec validate --all --strict`: passed.
- First smoke check exposed missing JAXB runtime support in SDK signing; added
  JAXB API 2.3.1 and a real SDK signing regression test, then repeated verification.
- Bounded live check: MQTT connected; QUOTE, SNAPSHOT, and TICK subscriptions
  accepted for AAPL. Received 161 events (QUOTE=73, SNAPSHOT=31, TICK=57).
- Invalid-event warnings during final live check: 0.
- Shutdown closed the market stream; smoke process is no longer running.
- Local redacted smoke evidence: `target/webull-smoke.log` (ignored build output).
- Only one application properties file exists. Credentials were preserved and
  checked absent from code, tests, specs, and documentation.

This verifies these credentials' observed access during the sample, not a general
promise that all Webull data is free, real-time, complete, or continuously available.
No subscription purchases or order/account mutations were performed.
