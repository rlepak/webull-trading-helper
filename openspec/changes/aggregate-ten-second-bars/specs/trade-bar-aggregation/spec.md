## Purpose

Provide short-interval candles from observed Webull trades for later analysis and deterministic replay.

## ADDED Requirements

### Requirement: Ten-second trade candles
The application SHALL aggregate positive-price TICK trades per symbol into fixed ten-second windows using event receive instants. Windows SHALL include their start and exclude their end. Candles SHALL contain decimal OHLC, observed trade count, volume and VWAP. Quotes and snapshots SHALL NOT contribute.

#### Scenario: Multiple trades and boundary
- **WHEN** trades priced 1.20, 1.40, 1.10 and 1.30 arrive within one window
- **THEN** open is 1.20, high 1.40, low 1.10 and close 1.30
- **AND** a trade exactly at the next ten-second boundary belongs to the next window

#### Scenario: Missing volume or invalid price
- **WHEN** a trade has no positive price
- **THEN** it is skipped and counted as invalid
- **AND** valid-price trades with missing or negative size still contribute to OHLC but make that bar's volume and VWAP unknown

#### Scenario: Known sizes
- **WHEN** every included trade has a nonnegative size
- **THEN** volume is their sum and VWAP is price-times-size sum divided by volume, or unknown for zero volume

### Requirement: Explicit timing and finality
Bars SHALL identify RECEIVE_TIME as their time basis and display offset-aware boundaries in America/Chicago by default. The core SHALL accept explicit advancement times without consulting a wall clock. Live processing SHALL close elapsed windows, including during idle periods, expose partial bars separately and never synthesize empty candles or revise completed bars.

#### Scenario: Idle finalization
- **WHEN** a window ends with no subsequent trade
- **THEN** the live timer makes its completed candle available on the next timer execution, normally within one second
- **AND** later empty windows produce no candles

#### Scenario: Late or backward time
- **WHEN** a trade targets a window before the monotonic completion boundary
- **THEN** it is counted as late without changing completed bars
- **AND** advancing the clock backwards does not reopen windows

#### Scenario: Repeated Chicago hour
- **WHEN** trades occur in both instances of the autumn repeated hour
- **THEN** their windows remain distinct by instant and offset

### Requirement: Bounded observable integration
Enabled aggregation SHALL receive accepted events independently of console logging and binary recording enablement. It SHALL retain bounded completed history and current bars through an application service, report cumulative completed and rejection counts in existing health summaries, and preserve the raw binary format. Disabled aggregation SHALL do no work.

#### Scenario: Retention and symbol churn
- **WHEN** configured bar or symbol retention limits are reached
- **THEN** the oldest completed bars or least-recently-traded inactive symbol history are evicted
- **AND** if every retained symbol has an active bar, new-symbol trades are rejected with a capacity counter instead of silently losing active bars

#### Scenario: Quiet recording and aggregation
- **WHEN** an accepted tick arrives with payload logging disabled
- **THEN** binary recording is offered the original event and enabled aggregation consumes it
- **AND** no candle payload is printed at normal log levels
