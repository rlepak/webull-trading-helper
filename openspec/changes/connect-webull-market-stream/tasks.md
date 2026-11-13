# Tasks

## 1. Configuration and dependency

- [x] 1.1 Add SDK dependency and single-file streaming settings with validation; verify configuration tests and document settings in HELP.md.

## 2. Market event processing

- [x] 2.1 Add immutable event POJOs, precise mapping, and safe JSON event logging; verify quote, snapshot, tick, malformed-event, and logging tests.

## 3. Socket lifecycle

- [x] 3.1 Add TLS MQTT connection, isolated subscriptions, reconnect/backoff, and shutdown; verify mocked lifecycle tests and disabled Spring startup.

## 4. Integration verification

- [x] 4.1 Run Maven tests and strict OpenSpec validation; perform a bounded live smoke attempt and record actual connection/subscription/data results or an external blocker without claiming unobserved success.
