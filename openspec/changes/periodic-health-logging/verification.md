# Verification

- `./mvnw test`: 36 tests passed, zero failures/errors/skips.
- `openspec validate --all --strict`: four active changes passed.
- Tests confirm quiet receive logging still offers events to binary recording,
  counters reset between summaries, no event payload appears in health output,
  and absent observations / failed / disabled recording are reported explicitly.
- An offline application smoke test disabled streaming and recording and overrode
  the health interval to 2000 ms for the test only. It emitted four health reports
  at approximately two-second intervals, all connected=false, recording=DISABLED,
  with never for absent event/poll times. No payload output appeared.
  Output: target/health-scheduler-smoke.log.
- The production property remains 300000 ms (five minutes), used for both initial
  and subsequent delay. We did not wait five minutes for this bounded check.
- Binary format and timestamp capture are unchanged; diagnostics now use DEBUG
  except health INFO and immediate warnings/errors.

## Startup visibility revision

Restored root INFO and the Spring Boot console banner. Routine application messages
remain DEBUG, market payload logging remains WARN, and health starts after five
minutes. Maven passed 36 tests; strict validation passed all four changes. An
eight-second offline startup check confirmed the banner and Started application
message, with no premature health report or market payload output. Evidence:
`target/startup-logging-smoke.log`.

## Immediate startup health summary

Added ApplicationReadyEvent handling alongside the unchanged five-minute scheduled
report. The Spring Boot integration test captures startup output with a 300000 ms
interval and verifies exactly one immediate health report, normal startup output,
truthful disconnected/disabled status, and no market payload output. All 36 Maven
tests passed; strict OpenSpec validation passed all four active changes.
