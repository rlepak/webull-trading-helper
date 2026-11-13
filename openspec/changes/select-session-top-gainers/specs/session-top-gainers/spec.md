## Purpose

Automatically select affordable US-stock top gainers for live Webull data according to the user's Chicago-time schedule.

## ADDED Requirements

### Requirement: Time-based ranking
The system SHALL select PRE_MARKET from 03:00 inclusive to 08:30 exclusive, DAY_1 from 08:30 inclusive to 15:00 exclusive, and AFTER_MARKET otherwise in America/Chicago, including daylight-saving changes. The schedule SHALL apply every day without holiday overrides.

#### Scenario: Session boundaries
- **WHEN** Chicago time reaches 03:00, 08:30, or 15:00
- **THEN** the next poll uses PRE_MARKET, DAY_1, or AFTER_MARKET respectively

#### Scenario: Overnight
- **WHEN** Chicago time is before 03:00 or at or after 15:00
- **THEN** the ranking is AFTER_MARKET

### Requirement: Scheduled gainers retrieval
When discovery and streaming are enabled, the system SHALL request the top 200 US-stock ranking entries sorted by descending percentage gain on a five-second schedule without overlapping or catch-up requests. Failed refreshes SHALL retain the last valid selection; HTTP 429 SHALL delay further discovery requests by at least 60 seconds, increasing for consecutive throttles.

#### Scenario: Normal polling
- **WHEN** a scheduled poll is due and no request is active
- **THEN** a single request retrieves the current session ranking

#### Scenario: Failed poll
- **WHEN** the response is unavailable or malformed
- **THEN** no empty replacement is inferred and a sanitized error is logged

#### Scenario: Throttle
- **WHEN** the API returns HTTP 429
- **THEN** the next discovery request waits at least 60 seconds and no immediate retry occurs

### Requirement: Price and symbol eligibility
The system SHALL select up to 10 highest-ranked eligible stocks for the active session, filtering before limiting. Eligible stocks SHALL have valid symbols, positive gains, and known prices from $0.10 through $10.00 inclusive. Successful empty results SHALL clear selection; fewer than 10 eligible stocks SHALL remain a smaller selection. Discovery SHALL NOT fall back to manual symbols.

#### Scenario: Boundary prices
- **WHEN** prices are 0.0999, 0.10, 10.00, and 10.0001
- **THEN** only the 0.10 and 10.00 stocks are eligible

#### Scenario: Invalid or empty results
- **WHEN** a successful ranking has invalid prices, duplicate symbols, or no eligible entries
- **THEN** invalid entries are excluded, duplicates collapse, and an empty selection removes existing subscriptions

### Requirement: Dynamic subscription reconciliation
The system SHALL add newly eligible symbols and remove departed symbols without reopening a healthy socket or resubscribing unchanged symbols. Failed operations SHALL remain pending for a paced retry. Reconnection SHALL restore the latest selection. An observed out-of-range trade or snapshot price SHALL remove the stock until a later valid ranking refresh permits it again.

#### Scenario: Membership change
- **WHEN** the selected set changes from A,B to B,C
- **THEN** A is unsubscribed, C is subscribed, and B remains subscribed

#### Scenario: Reorder only
- **WHEN** ranking order changes but membership does not
- **THEN** no subscription update is sent

#### Scenario: Price leaves range
- **WHEN** a selected stock reports a trade or snapshot price above 10.00 or below 0.10
- **THEN** it is removed from the desired set and its removal is reconciled on the live connection

#### Scenario: Reconnect and shutdown
- **WHEN** the socket reconnects
- **THEN** only the latest desired symbols are restored
- **AND** application shutdown stops polling and subscription work

#### Scenario: Top ten after filtering
- **WHEN** more than 10 ranking entries satisfy the price filter
- **THEN** only the highest-ranked 10 eligible stocks are selected

### Requirement: Shared application timezone
The application SHALL use America/Chicago for its shared clock, session selection, and application-generated log timestamps, including JSON receive-time display. Receive instants SHALL retain precision and provider timestamp fields SHALL remain unchanged.

#### Scenario: Seasonal offsets
- **WHEN** application timestamps are displayed in winter or summer
- **THEN** they use Chicago's corresponding UTC offset, independent of the host timezone
