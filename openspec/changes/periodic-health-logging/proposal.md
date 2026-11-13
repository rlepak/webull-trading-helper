## Why
Routine event and polling logs flood the console. The user wants a concise health log every five minutes while binary capture continues.

## What Changes
- Keep normal startup logs; suppress routine market-data console logging by default and retain optional DEBUG details.
- Emit one health summary every 300000 ms, plus an immediate summary when startup completes.
- Report connection state, active session, selected count, interval accepted/rejected events, last event/poll age, recorder state, written count and queue depth.
- Keep warnings/errors immediate; no sensitive payloads in health summaries.
- Configure in application.properties only.

## Capabilities
### New Capabilities
- periodic-health-logging: Scheduled operational summary.
### Modified Capabilities
None in main spec inventory. Supersedes routine INFO event logging described in the unarchived stream change.

## Impact
Adds a lifecycle-managed Spring scheduler and small status/counter fields. No new dependencies or endpoints. Binary format, receipt timestamps, selection and polling cadence remain unchanged.
