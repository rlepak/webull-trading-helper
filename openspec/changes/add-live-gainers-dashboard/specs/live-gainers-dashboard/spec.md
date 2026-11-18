## Purpose
Help the user monitor session top gainers and identify increases in observed trading activity in a live table.

## ADDED Requirements

### Requirement: Session table
The application SHALL serve a read-only table at /, refreshed every second, with up to ten selected tickers in ranking order, latest price, session gain percent, snapshot session volume, forming ten-second observed volume, trade count and volume multiplier. Unknown values SHALL remain unknown. Session boundaries SHALL follow existing Chicago ranking rules.

#### Scenario: Session transition
- **WHEN** the session or Chicago date changes
- **THEN** old session values and surge history are cleared and rows wait for the current session ranking

#### Scenario: Price and ranking updates
- **WHEN** the ranking or accepted trade/snapshot changes
- **THEN** the next table refresh reflects it, with gain labeled as ranking data and regular/extended snapshot volume selected for the active session

### Requirement: Volume surge
The application SHALL highlight green as the forming ten-second observed volume reaches at least three times the mean of the previous three consecutive completed ten-second bars. It SHALL retain a triggered highlight for ten seconds from the first trigger in each window; repeated updates in that window SHALL NOT extend it. Threshold, baseline count and highlight seconds SHALL be configurable in application.properties.

#### Scenario: Immediate threshold
- **WHEN** prior volumes are 100, 200, 300 and the forming volume reaches 600
- **THEN** green appears without waiting for candle close

#### Scenario: Unusable baseline
- **WHEN** three complete consecutive bars are unavailable, a volume is unknown, or the baseline is zero
- **THEN** no surge is signaled and the table explains warm-up or unavailable baseline

#### Scenario: Subscription and connection changes
- **WHEN** a symbol is newly observed, the session changes, or the feed reconnects
- **THEN** the initial partial window is excluded and fresh baseline bars are required

### Requirement: Honest feed status
The UI SHALL display connection status, last ranking time and row update age. Disconnection, stale rows or failed HTTP refreshes SHALL suppress green highlighting. Signals SHALL describe observed activity rather than order recommendations. API responses SHALL exclude credentials and use no-store caching.

#### Scenario: Feed loss
- **WHEN** the feed disconnects or a row has no event for the configurable stale interval (default 15 seconds)
- **THEN** that row cannot show an active surge

#### Scenario: Disabled recording
- **WHEN** the dashboard receives events with binary-log.enabled=false
- **THEN** it updates without writing raw data or placing orders

#### Scenario: Fixed highlight duration
- **WHEN** a surge first triggers at 07:00:41.500
- **THEN** its highlight expires at 07:00:51.500 unless a later window independently triggers another surge

### Requirement: Average observed trade size
The table SHALL show average trade size in shares for the forming ten-second window, calculated as observed volume divided by observed trade count and displayed with at most two decimal places. Unknown volume or zero trades SHALL display an em dash.

#### Scenario: Known trade sizes
- **WHEN** the window has 100 trades totaling 25000 shares
- **THEN** average trade size shows 250 shares per trade

#### Scenario: No usable average
- **WHEN** volume is unknown or trade count is zero
- **THEN** the average displays an em dash instead of zero or an invalid number
