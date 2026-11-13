# Tasks

## 1. Aggregation

- [x] 1.1 Implement immutable bars and bounded deterministic aggregation; verify OHLCV/VWAP, partial/final boundaries, ordering, invalid/late data, retention and Chicago DST with focused Maven tests and document semantics.
- [x] 1.2 Wire accepted events, idle finalization, single-file configuration and health counters; verify disabled/quiet behavior and timer integration with tests and document access/configuration.

## 2. Integration verification

- [x] 2.1 Run ./mvnw test and openspec validate --all --strict, record actual evidence and update REQ-005 status.
