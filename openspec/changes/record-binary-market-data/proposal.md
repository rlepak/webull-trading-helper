## Why
Persist the selected Webull stocks' market events in hourly binary files for later offline replay, following the sibling bots' length-prefixed record approach.

## What Changes
- Record accepted QUOTE, SNAPSHOT, and TICK events independently of console logging.
- Partition by Chicago session, MM-dd-yy date, and hour under data/.
- Preserve exact event contents, receive instant, run identifier and ordered sequence.
- Use bounded asynchronous writing, periodic durable flush, shutdown draining, exclusive directory ownership, and safe restart append.
- Configure only application.properties.

## Capabilities
### New Capabilities
- binary-market-recording: Durable hourly event capture for future replay.

### Modified Capabilities
None.

## Impact
Adds local disk writes and recorder lifecycle integration. No additional dependencies.
Replay execution, strategy/fill simulation, and selection/connection control-event recording are future work; this change records the accepted market events only. No new Webull endpoints.
