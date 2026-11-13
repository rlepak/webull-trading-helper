## ADDED Requirements

### Requirement: Five-minute health reporting
The application SHALL emit one concise operational summary every five minutes by default, plus one immediately when startup completes. Shutdown SHALL cancel scheduled reporting.

#### Scenario: Summary contents
- **WHEN** the reporting interval elapses
- **THEN** the log includes actual connection state, Chicago session, selected count, interval accepted/rejected counts, ages of last accepted event and successful poll, recorder status, cumulative written count and queue depth
- **AND** missing observations are reported as never and recording disabled is distinguished from healthy recording

### Requirement: Quiet console with continuous recording
Routine events, poll successes, selection changes and file rotations SHALL NOT appear at default console levels. Binary recording SHALL continue independently. Warnings and errors SHALL remain immediate and sanitized.

#### Scenario: Incoming event
- **WHEN** an event arrives under default logging settings
- **THEN** it is offered to binary recording and counted without printing its payload

#### Scenario: Recording failure
- **WHEN** recording fails
- **THEN** an immediate error and subsequent failed recording status remain visible

#### Scenario: Startup visibility
- **WHEN** the application starts
- **THEN** normal startup logs and the Spring Boot banner remain visible
- **AND** routine market-data payloads stay suppressed

#### Scenario: Initial health summary
- **WHEN** the application publishes its ready event
- **THEN** one health summary is logged immediately, alongside normal startup logs
- **AND** it reports current observed state without claiming a pending Webull connection is healthy
