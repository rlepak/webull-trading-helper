# Verification

Date: 2026-11-12.

- `./mvnw test`: 32 tests, zero failures/errors/skips.
- `openspec validate --all --strict`: all three active changes passed.
- Recorder tests cover precise data and ordering, shutdown drain, per-run sequence
  identity, append/restart, hourly/session/midnight routing, winter/summer and
  repeated autumn hour, periodic visibility after flush, exclusive ownership,
  incomplete tail recovery, rejection of corrupt complete frames, disabled mode,
  disk failure and queue overflow. Logger integration confirms snapshot recording
  when console event logging is OFF.
- Bounded read-only live check used a temporary recording directory under ignored
  target/ and console event logging at WARN. Captured 437 records:
  QUOTE=51, SNAPSHOT=109, TICK=277. All length prefixes and JSON frames decoded;
  sequence numbers were exactly 1 through 437 with Chicago -06:00 receive offsets.
- Output: `target/binary-live-check/market/11-12-26/webull-13.bin`, 270481 bytes.
  Startup and shutdown completed without recording errors. Diagnostic output:
  `target/binary-live-check.log`.
- Live coverage is regular market only; other session boundaries and DST use
  deterministic tests. No order operations or subscription purchases occurred.
- Enqueue is not a durable acknowledgment. Abrupt termination may lose pending
  queue/buffer data; normal operation forces files every configured interval,
  on rotation and shutdown. Recovery discards only incomplete final frames.
- Replay execution and selection/connection control records are follow-up work.

## Timing audit against momentum recordings

Read-only inspection of momentum-trading-bot/data/raw confirmed the format boundary.
A sample of up to 100 records per hourly file on available August 3–20 dates
showed bare payloads before August 10 and schema-v2 envelopes starting August 10.
Complete scans of representative dates then established:

| Dataset | Records scanned | Recorded receivedAt present |
| --- | ---: | ---: |
| Momentum 2026-08-07 | 306519 | 0 |
| Momentum 2026-08-10 | 283177 | 283177 |
| Momentum 2026-08-12 | 276969 | 276969 |
| Webull target/binary-live-check | 437 | 437 |

The pre-August-10 records retain provider event times where present but have no
recorded local arrival times. Momentum RawLogReplayReader substitutes synthetic
timestamps (epoch plus one millisecond per record) and labels them
LEGACY_RESEARCH_ONLY. That fallback cannot reconstruct actual arrival timing.
The sampled format boundary is not a complete audit of every historical date.

Both fully scanned later momentum days had valid timestamps, no synthetic flag,
no backward receive times within an hourly file, and no Chicago hour mismatch.
The current Webull capture likewise had no missing/invalid timestamps, backward
within-file times, hour/offset mismatch, or envelope/event receive-time mismatch.
Its receive intervals ranged from 74000 to 494960000 nanoseconds; timing is not
fabricated from sequence numbers.

Current Webull capture obtains the receive Instant in MarketDataLogger before
mapping/serialization and disk queuing. BinaryLogWriter uses that event Instant
for the record and routing; it does not replace it with disk-write time. This is
application-handler receive time, not a measured exchange/network arrival time.
Provider timestamps remain separate and unchanged. Chicago offset formatting
preserves the same Instant and nanoseconds.

The reopen validator previously checked only that the envelope receive field was
present. Hardened it to require parseable offset times in both envelope and event
and identical instants, rejecting invalid complete records without modifying them.
Added regression coverage for delayed disk writes retaining a 12345678-nanosecond
receive interval and invalid/missing/inconsistent receive-time rejection.

Replay runtime remains unimplemented. It must consume recorded receive times for
pacing and use sequence/run identity for order; it must not silently synthesize
missing time or use file names/provider times as arrival time. Wall-clock accuracy
still depends on the host clock; the recordings do not prove exchange latency.

Timing-audit validation: `./mvnw test` passed 34 tests, no failures/errors/skips.
`openspec validate --all --strict` passed all three active changes. Historical
momentum recordings and the existing Webull live capture were inspected read-only.
