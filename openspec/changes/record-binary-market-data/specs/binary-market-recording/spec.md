## ADDED Requirements

### Requirement: Replay-ready event records
When enabled, the recorder SHALL persist each accepted market event independently of console log level in length-prefixed binary files. Records SHALL include schema version, run ID, sequence, precise receive time with Chicago offset, and complete typed event data.

#### Scenario: Event order and contents
- **WHEN** quotes, snapshots and ticks are accepted
- **THEN** their recorded order, decimal precision, nulls and provider timestamps are preserved

### Requirement: Chicago session and hourly partitioning
The recorder SHALL route by received time to premarket from 03:00–08:30, market from 08:30–15:00, and aftermarket otherwise in America/Chicago. Paths SHALL be data/session/MM-dd-yy/webull-HH.bin by default, with one hourly file for all symbols.

#### Scenario: Session and date boundaries
- **WHEN** an event crosses 08:30, 15:00, an hour or midnight
- **THEN** it is written to the matching session, date and hour file

#### Scenario: Repeated DST hour
- **WHEN** Chicago repeats an hour in autumn
- **THEN** both occurrences append in order to the same file with distinct receive offsets

### Requirement: Durable bounded writer lifecycle
The recorder SHALL use a bounded queue and one writer, periodically force data to disk, drain on normal shutdown, and report overflow or write failure as terminal recording failure. Disabled recording SHALL create no files. Only one writer SHALL own a recording directory at a time.

#### Scenario: Flush and shutdown
- **WHEN** the flush interval expires or normal shutdown drains admitted records
- **THEN** buffered records are forced to disk

#### Scenario: Failure
- **WHEN** the queue is full or disk writing fails
- **THEN** recording becomes unhealthy, a sanitized error is logged and subsequent events are rejected for recording

### Requirement: Safe append on restart
The recorder SHALL append without overwriting complete records. On reopen it SHALL recover only a truncated final frame and reject invalid complete records or incompatible versions.

#### Scenario: Restart
- **WHEN** recording restarts during the same hour
- **THEN** existing complete records remain and new records have a distinct recording ID

#### Scenario: Interrupted write
- **WHEN** an hourly file ends with an incomplete frame
- **THEN** recovery removes that incomplete tail with a warning before appending
