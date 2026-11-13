## Purpose

Receive Webull market events as typed application data and log them for inspection before adding aggregation.

## ADDED Requirements

### Requirement: Configurable read-only connection
The application SHALL connect to Webull's TLS MQTT service when streaming is enabled, using credentials and subscriptions from application.properties. It SHALL subscribe only to a bounded configured symbol list and QUOTE, SNAPSHOT, or TICK types without buying subscriptions or submitting orders.

#### Scenario: Enabled stream
- **WHEN** streaming is enabled with valid credentials and symbols
- **THEN** the application establishes a uniquely identified session and requests the configured subscriptions
- **AND** it distinguishes connection success from subscription success

#### Scenario: Disabled or invalid configuration
- **WHEN** streaming is disabled
- **THEN** no Webull connection is attempted

#### Scenario: Missing credentials
- **WHEN** streaming is enabled with missing credentials or invalid subscriptions
- **THEN** startup fails with a configuration error that does not include credential values

### Requirement: Typed received market data
The application SHALL represent received quotes, snapshots, and tick batches as immutable application objects with symbol, instrument ID, source, event type, provider timestamps, and receive time. Decimal values SHALL retain precision and missing values SHALL remain missing.

#### Scenario: Received event
- **WHEN** a supported event arrives
- **THEN** its payload is converted to the appropriate typed object and logged at INFO level
- **AND** provider timestamps are retained without guessing their units

#### Scenario: Malformed event
- **WHEN** an event has invalid numeric data or lacks a valid symbol
- **THEN** a sanitized warning is logged and later valid events can still be processed

### Requirement: Observable failures and recovery
The application SHALL report connection and subscription failures without raw authentication responses or secrets. Failure of one requested data type SHALL NOT prevent attempting the others. Connection loss SHALL trigger reconnect attempts with increasing delay capped at 60 seconds and fresh subscriptions. Shutdown SHALL stop reconnect attempts and close the active stream.

#### Scenario: Entitlement failure
- **WHEN** Webull denies a data subscription
- **THEN** the application logs its data type and sanitized error category and continues attempting other requested types
- **AND** it does not claim the denied stream is available or free

#### Scenario: Connection loss
- **WHEN** the socket disconnects unexpectedly
- **THEN** the application closes the session and retries with backoff, restoring configured subscriptions after a successful connection

#### Scenario: Application shutdown
- **WHEN** the application stops
- **THEN** stream consumption and retries stop and client resources are closed
