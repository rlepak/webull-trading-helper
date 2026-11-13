package org.rl.webulltradinghelper.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.rl.webulltradinghelper.aggregation.MarketAggregationService;
import org.rl.webulltradinghelper.recording.BinaryLogProperties;
import org.rl.webulltradinghelper.recording.BinaryLogWriter;
import org.rl.webulltradinghelper.webull.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

@Component
public class HealthLogger {
    private static final Logger log = LoggerFactory.getLogger(HealthLogger.class);
    private final Clock clock;
    private final WebullStreamClient stream;
    private final SymbolSelection selection;
    private final MarketDataLogger events;
    private final TopGainersPoller poller;
    private final BinaryLogWriter recorder;
    private final BinaryLogProperties recording;
    private final MarketAggregationService aggregation;

    public HealthLogger(Clock clock, WebullStreamClient stream, SymbolSelection selection,
            MarketDataLogger events, TopGainersPoller poller,
            BinaryLogWriter recorder, BinaryLogProperties recording, MarketAggregationService aggregation) {
        this.clock = clock;
        this.stream = stream;
        this.selection = selection;
        this.events = events;
        this.poller = poller;
        this.recorder = recorder;
        this.recording = recording;
        this.aggregation = aggregation;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${health-log.interval-ms:300000}",
            initialDelayString = "${health-log.interval-ms:300000}")
    public synchronized void report() {
        Instant now = clock.instant();
        String recorderState = !recording.enabled() ? "DISABLED" : !recorder.isHealthy() ? "FAILED"
                : recorder.isRunning() ? "RUNNING" : "STOPPED";
        var bars = aggregation.stats();
        log.info("Health session={} connected={} selected={} eventsSinceReport={} rejectedSinceReport={} "
                        + "lastEventAgeSeconds={} lastGainersSuccessAgeSeconds={} recording={} writtenTotal={} queued={} "
                        + "aggregation={} activeBars={} barsCompletedTotal={} invalidTradesTotal={} lateTradesTotal={} capacityRejectedTradesTotal={}",
                GainersSession.at(now, clock.getZone()), stream.isConnected(), selection.symbols().size(),
                events.drainAcceptedCount(), events.drainRejectedCount(), age(now, events.lastReceivedAt()),
                age(now, poller.lastSuccessAt()), recorderState, recorder.writtenRecords(), recorder.queuedRecords(),
                aggregation.isEnabled() ? "ENABLED" : "DISABLED", bars.activeBars(), bars.completedTotal(),
                bars.invalidTrades(), bars.lateTrades(), bars.capacityRejectedTrades());
    }

    private static String age(Instant now, Instant observed) {
        return observed == null ? "never" : Long.toString(Duration.between(observed, now).getSeconds());
    }
}
