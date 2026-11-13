# Linux collector setup

## Prerequisites

- JDK 21 for building; Java 21 for running a prebuilt JAR.
- Stable outbound access to `api.webull.com:443` and `data-api.webull.com:1883` (TLS).
- A writable local recording directory and SDK cache directory.
- Automatic clock synchronization and sleep disabled during collection.
- Webull credentials with access to the requested market data.

Two CPU cores and 4 GB system RAM are a starting estimate, not a measured minimum.
The 8 GB mini PC has room for a 1 GB Java heap. Disk needs depend on activity and
retention; the recorder has no automatic deletion. Monitor free space.

## Configure the single application.properties

After cloning the repository, create `src/main/resources/application.properties`.
It is intentionally absent from Git. Set credentials manually in that file and use
the following collector settings. No profile, example or second properties file
is needed. The optional loopback server binding below limits the embedded HTTP
listener to this machine; Webull collection uses outbound connections.

Create its parent directory if it is absent from the fresh checkout:

```sh
mkdir -p src/main/resources
```

```properties
spring.application.name=webull-trading-helper
server.address=127.0.0.1
server.port=8080

webull.app-key=
webull.app-secret=
webull.region-id=us
webull.endpoint=api.webull.com
webull.streaming.enabled=true
webull.streaming.host=data-api.webull.com
webull.streaming.port=1883
webull.streaming.symbols=AAPL
webull.streaming.types=QUOTE,SNAPSHOT,TICK
webull.streaming.timeout-ms=10000

webull.top-gainers.enabled=true
webull.top-gainers.poll-interval-ms=5000
webull.top-gainers.zone=${app.time-zone}
webull.top-gainers.min-price=0.10
webull.top-gainers.max-price=10.00

app.time-zone=America/Chicago
binary-log.enabled=true
binary-log.directory=data
binary-log.queue-capacity=10000
binary-log.flush-interval-ms=1000
aggregation.enabled=true
aggregation.history-per-symbol=360
aggregation.max-symbols=100

health-log.interval-ms=300000
logging.level.root=INFO
logging.level.com.webull.openapi=OFF
logging.level.org.rl.webulltradinghelper.webull.MarketDataLogger=WARN
logging.level.org.rl.webulltradinghelper.health.HealthLogger=INFO
logging.pattern.dateformat=yyyy-MM-dd'T'HH:mm:ss.SSSXXX,${app.time-zone}
spring.main.banner-mode=console
```

The manual AAPL symbol is ignored while automatic top-gainer discovery is enabled.
Without this configuration, streaming and recording default to disabled. An app
that starts successfully without configuration is not necessarily collecting data.

## Build and start

From the repository root:

```sh
java -version
./mvnw verify
java -Xms256m -Xmx1g -jar target/webull-trading-helper-0.0.1-SNAPSHOT.jar \
  --spring.config.location=file:./src/main/resources/application.properties
```

The wrapper downloads Maven and dependencies on the first build. If a non-Git file
transfer lost the executable bit, run `chmod +x mvnw`. Always launch from the same
working directory: relative `data/` and `.webull/` paths are resolved from there.
Only one collector may own a recording directory. Stop with Ctrl+C or SIGTERM to
allow queued writes to drain; do not use SIGKILL for a normal stop.

The explicit `spring.config.location` loads your manually maintained file on each
start, overriding the default search locations. See Spring's
[external configuration reference](https://docs.spring.io/spring-boot/reference/features/external-config.html).

## Start automatically with systemd

This example assumes a checkout at `/opt/webull-trading-helper`, an existing
non-root service account named `webull`, and Java 21 at `/usr/bin/java`. Adjust those
paths/account to your server. The account must be able to read the JAR and the
properties file and write `data/` and `.webull/` under the working directory.

After building, install the release under a stable path before creating the service:

```sh
mkdir -p dist
cp target/webull-trading-helper-0.0.1-SNAPSHOT.jar dist/webull-trading-helper.jar
```

Save this unit as `/etc/systemd/system/webull-trading-helper.service` on Linux:

```ini
[Unit]
Description=Webull market-data collector
Wants=network-online.target
After=network-online.target

[Service]
Type=simple
User=webull
WorkingDirectory=/opt/webull-trading-helper
ExecStart=/usr/bin/java -Xms256m -Xmx1g -jar /opt/webull-trading-helper/dist/webull-trading-helper.jar --spring.config.location=file:/opt/webull-trading-helper/src/main/resources/application.properties
Restart=on-failure
RestartSec=10
TimeoutStopSec=90
UMask=0077

[Install]
WantedBy=multi-user.target
```

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now webull-trading-helper
sudo systemctl status webull-trading-helper
sudo journalctl -u webull-trading-helper -f
```

After a properties edit, use `sudo systemctl restart webull-trading-helper`.
For a new release, build first, stop the service, replace the JAR in `dist/`, then
start the service. Do not overwrite the JAR while its process is running.

`Restart=on-failure` restarts a failed process. It does not detect a stalled feed
or a recorder marked FAILED while the Java process remains alive. See the
[systemd service reference](https://www.freedesktop.org/software/systemd/man/latest/systemd.service.html).

## Confirm collection and transfer recordings

Startup prints one health summary; another appears every five minutes. After data
begins arriving, look for `connected=true`, a nonzero selection, increasing
`writtenTotal`, `recording=RUNNING`, recent event/poll ages and a queue that drains.
Startup alone, a connected socket or an empty queue does not prove data receipt.
Inspect warnings/errors if events remain absent. Check disk usage regularly.

Raw files are under `data/{premarket,market,aftermarket}/MM-dd-yy/webull-HH.bin`.
Ten-second candles are in memory; the raw events are the source for later replay.
The host's local timezone need not match Chicago, but its UTC clock must be accurate.

For a consistent copy of the entire directory, stop the service, copy from your Mac
with rsync, then start the service again. This creates a collection gap during the
copy. Replace `user@server` and the destination path with your own:

```sh
rsync -av --exclude='.writer.lock' \
  user@server:/opt/webull-trading-helper/data/ /path/to/mac/recordings/
```

To avoid stopping collection, transfer only finalized files, such as a completed
prior Chicago date, rather than the current file being appended. Keep originals
until the copy is verified. The binary format is portable; a replay/backtest runner
is still follow-up work.
