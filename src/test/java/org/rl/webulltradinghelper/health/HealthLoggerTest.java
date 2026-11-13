package org.rl.webulltradinghelper.health;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.webull.openapi.core.common.dict.SubscribeType;
import com.webull.openapi.data.quotes.domain.Snapshot;
import com.webull.openapi.data.quotes.subscribe.message.*;
import java.nio.file.Path;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.rl.webulltradinghelper.recording.*;
import org.rl.webulltradinghelper.aggregation.*;
import org.rl.webulltradinghelper.webull.*;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthLoggerTest {
    final Instant now = Instant.parse("2026-11-12T19:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneId.of("America/Chicago"));
    final MarketAggregationService aggregation = new MarketAggregationService(clock, new AggregationProperties(true, 360, 100));
    final WebullStreamClient stream = mock(WebullStreamClient.class);
    final SymbolSelection selection = mock(SymbolSelection.class);
    final TopGainersPoller poller = mock(TopGainersPoller.class);
    final BinaryLogWriter recorder = mock(BinaryLogWriter.class);

    @Test void quietEventsStillRecordAndSummaryDrainsCounts() {
        Logger receiverLog = (Logger) LoggerFactory.getLogger(MarketDataLogger.class);
        Logger healthLog = (Logger) LoggerFactory.getLogger(HealthLogger.class);
        Level oldReceiver = receiverLog.getLevel(), oldHealth = healthLog.getLevel();
        ListAppender<ILoggingEvent> received = new ListAppender<>(), health = new ListAppender<>();
        received.start(); health.start(); receiverLog.addAppender(received); healthLog.addAppender(health);
        try {
            receiverLog.setLevel(Level.WARN); healthLog.setLevel(Level.INFO);
            var events = new MarketDataLogger(new MarketDataMapper(), JsonMapper.builder().build(), clock, recorder, aggregation);
            Snapshot payload = new Snapshot(); payload.setSymbol("TEST"); payload.setPrice("1.25");
            events.receive(new MarketData(new Metadata(SubscribeType.SNAPSHOT), new QuotesPublish<>("provider-time", payload)));
            verify(recorder).record(any());
            assertTrue(received.list.isEmpty());
            when(stream.isConnected()).thenReturn(true);
            when(selection.symbols()).thenReturn(Set.of("TEST"));
            when(poller.lastSuccessAt()).thenReturn(now.minusSeconds(3));
            when(recorder.isHealthy()).thenReturn(true);
            when(recorder.isRunning()).thenReturn(true);
            when(recorder.writtenRecords()).thenReturn(1L);
            var reporter = new HealthLogger(clock, stream, selection, events, poller, recorder,
                    new BinaryLogProperties(true, Path.of("unused"), 1, 1000), aggregation);
            reporter.report(); reporter.report();
            String first = health.list.getFirst().getFormattedMessage();
            assertTrue(first.contains("session=DAY_1 connected=true selected=1"));
            assertTrue(first.contains("eventsSinceReport=1"));
            assertTrue(first.contains("lastEventAgeSeconds=0"));
            assertTrue(first.contains("lastGainersSuccessAgeSeconds=3"));
            assertTrue(first.contains("recording=RUNNING writtenTotal=1 queued=0"));
            assertTrue(first.contains("aggregation=ENABLED activeBars=0 barsCompletedTotal=0"));
            assertTrue(first.contains("invalidTradesTotal=0 lateTradesTotal=0 capacityRejectedTradesTotal=0"));
            assertFalse(first.contains("1.25")); assertFalse(first.contains("provider-time"));
            assertTrue(health.list.getLast().getFormattedMessage().contains("eventsSinceReport=0"));
        } finally {
            receiverLog.detachAppender(received); healthLog.detachAppender(health);
            received.stop(); health.stop(); receiverLog.setLevel(oldReceiver); healthLog.setLevel(oldHealth);
        }
    }

    @Test void reportsAbsentDataAndFailedOrDisabledRecorderTruthfully() {
        Logger logger = (Logger) LoggerFactory.getLogger(HealthLogger.class);
        Level previous = logger.getLevel();
        var capture = new ListAppender<ILoggingEvent>(); capture.start(); logger.addAppender(capture);
        try {
            logger.setLevel(Level.INFO);
            when(selection.symbols()).thenReturn(Set.of());
            var events = mock(MarketDataLogger.class);
            new HealthLogger(clock, stream, selection, events, poller, recorder,
                    new BinaryLogProperties(true, Path.of("unused"), 1, 1000), aggregation).report();
            String failure = capture.list.getFirst().getFormattedMessage();
            assertTrue(failure.contains("connected=false"));
            assertTrue(failure.contains("lastEventAgeSeconds=never"));
            assertTrue(failure.contains("lastGainersSuccessAgeSeconds=never"));
            assertTrue(failure.contains("recording=FAILED"));
            new HealthLogger(clock, stream, selection, events, poller, recorder,
                    new BinaryLogProperties(false, Path.of("unused"), 1, 1000), aggregation).report();
            assertTrue(capture.list.getLast().getFormattedMessage().contains("recording=DISABLED"));
        } finally { logger.detachAppender(capture); capture.stop(); logger.setLevel(previous); }
    }
}
