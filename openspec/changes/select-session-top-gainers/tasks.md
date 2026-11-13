# Tasks

## 1. Session selection and discovery

- [x] 1.1 Implement single-file settings, Chicago session selection, SDK retrieval, price filtering, and paced polling; verify boundary, DST, filtering, empty/error, and throttle tests and document the schedule.

## 2. Live subscription updates

- [x] 2.1 Reconcile per-type subscription differences on the current session, handle live price exits, and restore current selection after reconnect; verify delta, failed-update, empty-selection, and lifecycle tests.

## 3. Integration verification

- [x] 3.1 Run the full Maven suite and strict OpenSpec validation, perform a bounded read-only live check, and record observed results and limitations in the requirement and change.

## 4. Requested top-ten selection

- [x] 4.1 Limit active-session selection to the top 10 after filtering, update regression coverage and documentation, and run Maven tests and strict OpenSpec validation.

## 5. Shared Chicago time

- [x] 5.1 Unify the shared clock, session timezone and application-generated log timestamps around Chicago; verify winter/summer display and run Maven and strict spec checks.
