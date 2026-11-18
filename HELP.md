# Webull Trading Helper

See [README.md](README.md) for the quick start and [Linux setup](docs/LINUX.md)
for server configuration, startup and transferring recordings.

## Spec-driven development with OpenSpec

This project uses OpenSpec's `spec-driven` schema and its generated Codex skills.
Project instructions live in [AGENTS.md](AGENTS.md); context and artifact rules
live in [openspec/config.yaml](openspec/config.yaml).

### Setup

OpenSpec 1.17.0 requires Node.js 22.12.0 or newer. Install the CLI:

```sh
npm install -g @fission-ai/openspec@1.17.0
openspec --version
```

The project is already initialized for Codex. On a fresh checkout, the committed
`.agents/skills/` files provide the integration. Start a new Codex session if the
skills are not visible. To regenerate the integration, run:

```sh
openspec init --tools codex --profile core
```

### Workflow in Codex

Track the implementation order and progress in [REQUIREMENTS.md](REQUIREMENTS.md).
Select one requirement at a time and link it to its OpenSpec change.

1. Explore uncertain requirements with `$openspec-explore`.
2. Run `$openspec-propose <description>` to create a change with its proposal,
   capability specs, design where needed, and implementation tasks.
3. Review the artifacts under `openspec/changes/<change-name>/`.
4. Run `$openspec-apply-change <change-name>` to implement the change and track tasks.
5. Validate specs and run the checks appropriate to the change:

   ```sh
   openspec validate --all --strict
   ./mvnw test
   ```

6. Run `$openspec-archive-change <change-name>` after implementation and verification.
   The archive workflow reconciles spec deltas into the main specs and stores the
   completed change under `openspec/changes/archive/`.

Use `$openspec-update-change` to revise an active change and
`$openspec-sync-specs` to sync deltas without archiving. In Codex desktop, select
the corresponding skill from the Skills sidebar if typing its name is unavailable.

### Project artifacts

- `REQUIREMENTS.md`: ordered backlog, open product decisions, and requirement status.
- `openspec/specs/`: current capability requirements; initially empty.
- `openspec/changes/`: active proposals, spec deltas, designs, and task lists.
- `openspec/changes/archive/`: completed change history.
- `.agents/skills/`: generated OpenSpec integration; customize project rules in
  `openspec/config.yaml` rather than editing generated skills.

Useful commands:

```sh
openspec list
openspec list --specs
openspec status --change <change-name>
openspec validate <change-name> --strict
```

The first requirement, REQ-001, covers a Webull connection and useful free data
streaming with typed event logging. Track progress in [REQUIREMENTS.md](REQUIREMENTS.md).

### Local Webull configuration

`src/main/resources/application.properties` contains local Webull credentials and
is ignored by Git. Do not print or commit it. Use only this configuration file;
for a fresh checkout, create it locally and populate the required settings.
Current credentials were copied from the
existing momentum/scalping projects. A bounded live check on 2026-11-10 verified
MQTT connection, subscription acceptance, and receipt of market events.

Spring Boot copies resource files into build output, so a build made with this
local file will contain credentials. Do not share that build artifact; use blank
or externally supplied credentials for distributable builds.

### Running the market stream

The POM includes Webull SDK 1.1.20 and JAXB API 2.3.1 for its Java 21 signing compatibility.
Run `./mvnw spring-boot:run`. The local application.properties enables streaming
with automatic top-gainer discovery. Change these settings in that same file:

| Property | Meaning / initial value |
| --- | --- |
| `webull.app-key`, `webull.app-secret` | Existing private credentials |
| `webull.region-id` | `us` |
| `webull.endpoint` | HTTP API host, `api.webull.com` |
| `webull.streaming.enabled` | `true`; use `false` for offline startup |
| `webull.streaming.host` | MQTT host, `data-api.webull.com` |
| `webull.streaming.port` | `1883`, with TLS always enabled |
| `webull.streaming.symbols` | Manual fallback only when top-gainer discovery is disabled (maximum 50) |
| `webull.streaming.types` | `QUOTE,SNAPSHOT,TICK`; requests each separately |
| `webull.streaming.timeout-ms` | `10000`; allowed range 1000–60000 |

### Application time

`app.time-zone=America/Chicago` in application.properties is the shared timezone.
The application clock, console log dates, and JSON `receivedAt` display use it.
The session selector references the same setting. Chicago daylight saving rules
apply automatically. Event objects retain exact receive instants and original
Webull timestamp fields; offset display does not change the recorded instant.
Binary recording uses this timezone for session/date/hour paths; future replay will use it too.

### Five-minute health logs

Normal startup logs and the Spring Boot banner remain visible. Routine market-data messages are suppressed. `health-log.interval-ms=300000` emits
a summary every five minutes. One additional health summary appears immediately when startup completes, alongside the normal startup logs. It reports the
Chicago session, actual MQTT connection state, selected count, accepted/rejected
events since the previous report, last event/poll success ages, recording state,
cumulative written count and pending queue depth. Missing observations show
`never`. A connected socket alone does not establish data freshness or recording
health; written count is not a durability acknowledgment.

Root logging and the health logger are INFO; routine application messages use DEBUG and the market-data logger is WARN. Warnings/errors remain
immediate. Binary recording continues with detailed console logging disabled.
For troubleshooting, individual application loggers can be set to DEBUG in the
same application.properties. SDK request logging remains OFF.

### Binary market recordings

Recording is enabled locally in the existing application.properties:

| Property | Value |
| --- | --- |
| `binary-log.enabled` | `true` |
| `binary-log.directory` | `data` |
| `binary-log.queue-capacity` | `10000` |
| `binary-log.flush-interval-ms` | `1000` |

Files contain all accepted selected-stock events, regardless of console log level:

```text
data/premarket/11-12-26/webull-08.bin
data/market/11-12-26/webull-08.bin
data/market/11-12-26/webull-09.bin
data/aftermarket/11-12-26/webull-15.bin
```

The three session folders are created on startup; day/hour files are created when
events arrive. Routing uses receive time and the Chicago schedule. Restarts append.
During the repeated autumn hour, both occurrences share the hourly file, with exact
offsets and sequence preserving their order. One process owns a recording directory.

Like the sibling bots, each frame is a four-byte big-endian length followed by
UTF-8 JSON. Version 1 records contain `schemaVersion`, `recordingId`,
`sequenceNumber`, `receivedAt` and the complete `event`. This is binary framing,
not compression or a byte-compatible copy of the other bots' event schema.
Sequences increase within a run; recording IDs distinguish restarts. Decimal
precision, null fields and original provider timestamps are preserved.

The background writer forces buffered records to disk every second, on file
rotation and on normal shutdown. Enqueue does not mean durable; abrupt termination
can lose pending records. Overflow or disk failure logs ERROR and marks recording
unhealthy; it never silently reports successful capture. Restart recovery removes
only an incomplete final frame, with a warning; invalid complete frames fail closed.
The data directory is ignored by Git.

This increment records accepted QUOTE, SNAPSHOT and TICK events. Replay execution,
selection/connection control records and trading simulation remain follow-up work.

### Ten-second trade candles

Accepted TICK entries form separate candles for each symbol at `:00–:10`, `:10–:20`,
and subsequent ten-second boundaries. The end is exclusive. Candles contain decimal
open/high/low/close, observed trade count, summed trade volume and volume-weighted price.
Quotes and daily snapshots do not contribute. Nonpositive/missing prices are skipped.
Missing/negative sizes make volume and VWAP unknown; zero total size also gives unknown VWAP.

These are **receive-time candles**, using the exact `event.receivedAt` saved in binary
records. Boundaries display Chicago offsets, including DST. They may differ from Webull's
exchange-time chart. No provider timestamp units or time-only trade dates are guessed.
Repeated tick entries are counted as observed: the current feed model has no unique trade ID.

Completed means the window elapsed, not that all exchange trades were received. Partial
bars remain separate, empty periods produce no artificial candles, and completed candles
never change. Backward/late timestamps cannot reopen closed windows and increment counters.
The clock-independent core accepts explicit advancement times for future binary replay;
an unfinished interval should remain partial when replay stops inside it.

Configuration stays in the existing application.properties:

| Property | Default |
| --- | --- |
| `aggregation.enabled` | `true` |
| `aggregation.history-per-symbol` | `360` completed bars (up to one hour of continuously populated windows) |
| `aggregation.max-symbols` | `100` retained symbols across selection changes |

The live timer checks elapsed windows every second. Existing startup/five-minute health
logs include enabled state, active bars, completed total and invalid/late/capacity rejection
counts. No individual candle is printed. Startup starts fresh in memory; raw binary files
are unchanged. Oldest completed bars are discarded at the history limit; symbol churn evicts
the least-recently-traded inactive symbol. Capacity rejection preserves existing active bars.

Inject `MarketAggregationService` to read `completedBars("PLCE")` in ascending time order
or `currentBar("PLCE")` for an immutable optional partial candle. Retained history can include
symbols no longer selected; check current selection before using it for later decisions.
There is no HTTP endpoint or separate candle file in this increment.

### Automatic top gainers

When `webull.top-gainers.enabled=true`, the manual symbol list is ignored. The
app starts with no selected stocks and polls the first 200 ranked US stocks
using descending percentage gain. It follows the highest-ranked 10 eligible gainers priced
between $0.10 and $10.00 inclusive, as requested for each active session.

| Chicago local time | Webull ranking |
| --- | --- |
| 03:00 inclusive–08:30 exclusive | `PRE_MARKET` |
| 08:30 inclusive–15:00 exclusive | `DAY_1` |
| All other times | `AFTER_MARKET` |

This uses `America/Chicago`, following daylight saving time. The requested
wall-clock schedule applies every day, including weekends, holidays, and overnight;
it is not an exchange calendar and does not imply the market is open.

| Property | Configured value |
| --- | --- |
| `webull.top-gainers.enabled` | `true` |
| `webull.top-gainers.poll-interval-ms` | `5000` |
| `webull.top-gainers.zone` | `America/Chicago` |
| `webull.top-gainers.min-price` | `0.10` |
| `webull.top-gainers.max-price` | `10.00` |

Polls do not overlap or catch up in bursts. A failed refresh retains the last
valid selection; 429 responses pause polling for at least 60 seconds with
increasing backoff. A successful empty eligible list removes all subscriptions.
Missing/malformed prices and nonpositive gains are excluded.

A healthy socket stays connected. The worker adds/removes only changed symbols,
in batches up to 50 per event type, with at least 1.1 seconds between operations.
Failed updates remain pending for retry. During reconnect, it restores the latest
selection. Received trades or snapshots showing an out-of-range price remove the
stock; a later valid ranking can requalify it. Quote bid/ask prices alone do not
trigger that removal. Removed-symbol events are suppressed locally while the
server-side unsubscribe completes. Logs report the session, returned/eligible
counts, changed symbols, and subscription updates.

The socket uses a unique session ID and retries connection loss with delays from
2 to 60 seconds. Optional DEBUG logs distinguish MQTT connection, accepted subscriptions,
and actual received events. A subscription acceptance is not proof of event receipt
or real-time entitlement. Denied subscriptions are reported without buying data.

`MarketDataEvent` contains immutable typed quote, snapshot, or tick payloads.
Decimals use BigDecimal; original provider timestamps are preserved without unit
conversion. Each accepted event is recorded in binary; optional DEBUG output from `MarketDataLogger` displays single-line JSON.
Set `logging.level.org.rl.webulltradinghelper.webull.MarketDataLogger=WARN` to suppress
event logs. Keep `logging.level.com.webull.openapi=OFF` to suppress SDK request logs.
SDK token cache files, if created, are under ignored `.webull/`.

Run `./mvnw test` for offline tests; the Spring test disables streaming explicitly.
There are no account operations, historical downloads, or order calls
in this first implementation. See the active OpenSpec change for its requirements.

Official references: [OpenSpec](https://github.com/Fission-AI/OpenSpec),
[CLI reference](https://github.com/Fission-AI/OpenSpec/blob/main/docs/cli.md),
[Codex integration](https://github.com/Fission-AI/OpenSpec/blob/main/docs/supported-tools.md).

## Live gainers dashboard (REQ-006)

Run the application normally and open `http://localhost:8080/` (or your server's
address and configured port). `/api/dashboard` is the read-only, uncached table
snapshot. The page refreshes every second without adding broker requests.

The table follows the existing Chicago schedule: premarket 03:00–08:30, regular
08:30–15:00, aftermarket otherwise. Tickers stay in provider ranking order.
Price updates from matching-session trades/snapshots; gain % is the latest
ranking value, not recalculated from live prices. Session volume is the broker
snapshot field; ten-second volume and count include only observed trades.

A forming receive-time ten-second bar triggers green at 3× the average of the
previous three consecutive complete bars. Green lasts ten seconds from the first trigger in a window; repeated updates
in that same window do not extend it. The first partial subscription window is excluded. Missing windows,
unknown sizes or a zero baseline do not trigger. Session/date changes and
reconnections require fresh warm-up. Stale ranking/events and disconnected feeds
suppress highlights; green indicates activity, not a buy recommendation.

Settings in the existing local `src/main/resources/application.properties`:

```properties
binary-log.enabled=false
aggregation.enabled=true
dashboard.volume-multiplier=3
dashboard.baseline-bars=3
dashboard.highlight-seconds=10
dashboard.stale-seconds=15
```

Keep aggregation history at least as large as the baseline count. No raw log
files are written with recording disabled. The dashboard has no order controls
and uses only existing Webull data subscriptions. Missing broker fields display
as a dash. The existing configuration file remains ignored to protect credentials.
