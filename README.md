# Webull Trading Helper

Java 21 / Spring Boot collector for Webull US-stock market data.

- Polls session top gainers every five seconds, with failure backoff.
- Follows the highest-ranked 10 stocks priced $0.10–$10.00 with positive gains.
- Receives quotes, snapshots and trades over TLS MQTT.
- Records raw events in hourly binary files with precise receive timestamps.
- Builds receive-time ten-second OHLCV/VWAP candles in bounded memory.
- Reports health at startup and every five minutes.

Session selection and file partitioning use America/Chicago. Replay/backtesting,
strategy execution and order submission are not implemented yet.

## Run the collector

Use JDK 21 and the included Maven wrapper. No separate Maven, Node.js, database
or IDE is needed to run the packaged application.

1. Create/update the ignored `src/main/resources/application.properties` manually.
   [Linux setup](docs/LINUX.md) lists the collector settings with blank credential fields.
2. From the project root, build and test:

   ```sh
   ./mvnw verify
   ```

3. Start using that same configuration file:

   ```sh
   java -Xms256m -Xmx1g -jar target/webull-trading-helper-0.0.1-SNAPSHOT.jar \
     --spring.config.location=file:./src/main/resources/application.properties
   ```

The explicit file location makes edits take effect on restart without rebuilding.
The file is required by this command; a missing file fails startup.

See [Linux setup](docs/LINUX.md) for unattended operation and transferring recordings.
See [HELP.md](HELP.md) for data semantics and the OpenSpec development workflow.

## Source control and data

Commit source, tests, the Maven wrapper, documentation and OpenSpec artifacts.
Local credentials, SDK caches, `data/`, `target/`, `dist/`, `outputs/`, logs and
IDE/macOS metadata are ignored. Existing local recordings and exports are not deleted.

Maven includes resource files in its build output: a JAR built with your private
application.properties contains that configuration. Transfer source via Git and
build on the server, or build a distributable JAR from a clean copy without that file.
Configure the single application.properties on the destination before starting it.

## Development checks

```sh
./mvnw test
openspec validate --all --strict
```

OpenSpec is needed for development only. [REQUIREMENTS.md](REQUIREMENTS.md) tracks
the implemented increments and remaining scope.
