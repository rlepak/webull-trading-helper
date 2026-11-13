package org.rl.webulltradinghelper.webull;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.webull.openapi.core.common.dict.SubscribeType;
import com.webull.openapi.data.quotes.domain.Snapshot;
import org.junit.jupiter.api.Test;
import java.time.*;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class MarketDataLoggerTest {
    @Test void quietTickIsBothRecordedAndAggregatedWithIdenticalReceiveTime() {
        Logger logger = (Logger) LoggerFactory.getLogger(MarketDataLogger.class);
        var originalLevel = logger.getLevel();
        var recorder = org.mockito.Mockito.mock(org.rl.webulltradinghelper.recording.BinaryLogWriter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T19:00:01.123456789Z"), ZoneId.of("America/Chicago"));
        var aggregation = new org.rl.webulltradinghelper.aggregation.MarketAggregationService(clock,
                new org.rl.webulltradinghelper.aggregation.AggregationProperties(true, 360, 100));
        ListAppender<ILoggingEvent> capture = new ListAppender<>(); capture.start(); logger.addAppender(capture);
        try {
            logger.setLevel(ch.qos.logback.classic.Level.WARN);
            var receiver = new MarketDataLogger(new MarketDataMapper(), JsonMapper.builder().build(), clock, recorder, aggregation);
            var tick = new com.webull.openapi.data.quotes.domain.Tick();
            var trade = new com.webull.openapi.data.quotes.domain.TickRecord();
            trade.setPrice("0.4714"); trade.setVolume("366"); trade.setTime("14:00:01");
            tick.setResult(java.util.List.of(trade));
            receiver.receive(MarketDataMapperTest.message(tick, SubscribeType.TICK));
            var captor = org.mockito.ArgumentCaptor.forClass(org.rl.webulltradinghelper.webull.model.MarketDataEvent.class);
            org.mockito.Mockito.verify(recorder).record(captor.capture());
            assertEquals(clock.instant(), captor.getValue().receivedAt());
            var bar = aggregation.currentBar("AAPL").orElseThrow();
            assertEquals(new java.math.BigDecimal("0.4714"), bar.close());
            assertEquals(new java.math.BigDecimal("366"), bar.volume());
            assertEquals(Instant.parse("2026-11-12T19:00:00Z"), bar.start().toInstant());
            assertEquals(1, bar.tradeCount());
            assertTrue(capture.list.isEmpty());
        } finally { logger.detachAppender(capture); capture.stop(); logger.setLevel(originalLevel); }
    }

    @Test void snapshotIsRecordedWhenConsoleLoggingIsDisabled() {
        Logger logger = (Logger) LoggerFactory.getLogger(MarketDataLogger.class);
        var originalLevel = logger.getLevel();
        var recorder = org.mockito.Mockito.mock(org.rl.webulltradinghelper.recording.BinaryLogWriter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T19:00:00Z"), ZoneId.of("America/Chicago"));
        try {
            logger.setLevel(ch.qos.logback.classic.Level.OFF);
            var receiver = new MarketDataLogger(new MarketDataMapper(), JsonMapper.builder().build(), clock, recorder, org.mockito.Mockito.mock(org.rl.webulltradinghelper.aggregation.MarketAggregationService.class));
            Snapshot snapshot = new Snapshot(); snapshot.setPrice("1.23456789");
            receiver.receive(MarketDataMapperTest.message(snapshot, SubscribeType.SNAPSHOT));
            var captor = org.mockito.ArgumentCaptor.forClass(org.rl.webulltradinghelper.webull.model.MarketDataEvent.class);
            org.mockito.Mockito.verify(recorder).record(captor.capture());
            assertEquals("SNAPSHOT", captor.getValue().type());
            assertEquals(clock.instant(), captor.getValue().receivedAt());
        } finally { logger.setLevel(originalLevel); }
    }

    @Test void sharedClockAndSummerReceiveTimeUseChicago() {
        Clock clock = new WebullConfiguration().webullClock("America/Chicago");
        assertEquals(ZoneId.of("America/Chicago"), clock.getZone());
        Logger logger = (Logger) LoggerFactory.getLogger(MarketDataLogger.class);
        var previous = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        ListAppender<ILoggingEvent> capture = new ListAppender<>(); capture.start(); logger.addAppender(capture);
        try {
            var receiver = new MarketDataLogger(new MarketDataMapper(), JsonMapper.builder().build(),
                    Clock.fixed(Instant.parse("2026-07-12T19:00:00Z"), clock.getZone()), org.mockito.Mockito.mock(org.rl.webulltradinghelper.recording.BinaryLogWriter.class), org.mockito.Mockito.mock(org.rl.webulltradinghelper.aggregation.MarketAggregationService.class));
            Snapshot snapshot = new Snapshot(); snapshot.setPrice("1");
            receiver.receive(MarketDataMapperTest.message(snapshot, SubscribeType.SNAPSHOT));
            assertTrue(capture.list.getFirst().getFormattedMessage().contains("2026-07-12T14:00-05:00"));
        } finally { logger.detachAppender(capture); capture.stop(); logger.setLevel(previous); }
    }

    @Test void malformedEventDoesNotPreventNextEventAndLogsStaySingleLine() {
        Logger logger = (Logger) LoggerFactory.getLogger(MarketDataLogger.class);
        var previous = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        ListAppender<ILoggingEvent> capture = new ListAppender<>(); capture.start(); logger.addAppender(capture);
        try {
            var receiver = new MarketDataLogger(new MarketDataMapper(), JsonMapper.builder().build(), Clock.fixed(Instant.parse("2026-11-12T19:00:00.123456789Z"), ZoneId.of("America/Chicago")), org.mockito.Mockito.mock(org.rl.webulltradinghelper.recording.BinaryLogWriter.class), org.mockito.Mockito.mock(org.rl.webulltradinghelper.aggregation.MarketAggregationService.class));
            Snapshot snapshot = new Snapshot(); snapshot.setPrice("private-value");
            var message = MarketDataMapperTest.message(snapshot, SubscribeType.SNAPSHOT);
            receiver.receive(message);
            snapshot.setPrice("12.50"); snapshot.setTradingSession("PRE\nMARKET");
            receiver.receive(message);
            assertEquals(2, capture.list.size());
            assertFalse(capture.list.getFirst().getFormattedMessage().contains("private-value"));
            String logged = capture.list.getLast().getFormattedMessage();
            assertTrue(logged.contains("\"type\":\"SNAPSHOT\""));
            assertTrue(logged.contains("12.50"));
            assertTrue(logged.contains("2026-11-12T13:00:00.123456789-06:00"));
            assertFalse(logged.contains("\n"));
        } finally { logger.detachAppender(capture); capture.stop(); logger.setLevel(previous); }
    }
}
