package org.rl.webulltradinghelper.aggregation;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent.*;
import static org.junit.jupiter.api.Assertions.*;

class TenSecondBarAggregatorTest {
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final Instant START = Instant.parse("2026-11-12T20:00:00Z");
    private final TenSecondBarAggregator bars = new TenSecondBarAggregator(CHICAGO, 360, 100);

    @Test void computesOhlcvFromAllTradesAndSeparatesBoundaryAndSymbols() {
        bars.accept(tick("PLCE", START.plusNanos(1), trade("1.20", "10"), trade("1.40", "20")));
        bars.accept(tick("PLCE", START.plusSeconds(9).plusNanos(999999999), trade("1.10", "30"), trade("1.30", "40")));
        bars.accept(tick("OTHER", START.plusSeconds(9), trade("9", "1")));
        TenSecondBar partial = bars.currentBar("PLCE").orElseThrow();
        assertFalse(partial.completed());
        assertTrue(bars.completedBars("PLCE").isEmpty());
        bars.accept(tick("PLCE", START.plusSeconds(10), trade("2", "5")));
        TenSecondBar bar = bars.completedBars("PLCE").getFirst();
        assertTrue(bar.completed());
        assertEquals(new BigDecimal("1.20"), bar.open());
        assertEquals(new BigDecimal("1.40"), bar.high());
        assertEquals(new BigDecimal("1.10"), bar.low());
        assertEquals(new BigDecimal("1.30"), bar.close());
        assertEquals(new BigDecimal("100"), bar.volume());
        assertEquals(0, new BigDecimal("1.25").compareTo(bar.vwap()));
        assertEquals(4, bar.tradeCount());
        assertEquals(TenSecondBar.TimeBasis.RECEIVE_TIME, bar.timeBasis());
        assertEquals(OffsetDateTime.parse("2026-11-12T14:00:00-06:00"), bar.start());
        assertEquals(START.plusSeconds(10), bar.end().toInstant());
        assertEquals(new BigDecimal("2"), bars.currentBar("PLCE").orElseThrow().open());
        assertEquals(new BigDecimal("9"), bars.completedBars("OTHER").getFirst().close());
        assertEquals(4, partial.tradeCount()); // previously obtained snapshots cannot mutate
    }

    @Test void idleFinalizationRejectsLateTradesAndNeverCreatesEmptyWindows() {
        bars.accept(tick("PLCE", START.plusSeconds(2), trade("1", "2")));
        bars.advanceTo(START.plusSeconds(9));
        assertTrue(bars.completedBars("PLCE").isEmpty());
        bars.advanceTo(START.plusSeconds(10));
        bars.advanceTo(START.minusSeconds(60));
        bars.accept(tick("PLCE", START.plusSeconds(8), trade("8", "2")));
        bars.advanceTo(START.plusSeconds(3600));
        assertEquals(1, bars.completedBars("PLCE").size());
        assertTrue(bars.currentBar("PLCE").isEmpty());
        assertEquals(1, bars.stats().lateTrades());
        assertEquals(1, bars.stats().completedTotal());
    }

    @Test void outOfOrderOpenWindowUsesReceiveOrderAndEqualTimesUseListOrder() {
        bars.accept(tick("PLCE", START.plusSeconds(8), trade("8", "1")));
        bars.accept(tick("PLCE", START.plusSeconds(2), trade("2", "1")));
        bars.accept(tick("PLCE", START.plusSeconds(8), trade("7", "1"), trade("6", "1")));
        TenSecondBar bar = bars.currentBar("PLCE").orElseThrow();
        assertEquals(new BigDecimal("2"), bar.open());
        assertEquals(new BigDecimal("6"), bar.close());
        assertEquals(new BigDecimal("8"), bar.high());
        assertEquals(4, bar.tradeCount());
    }

    @Test void unknownSizesRemainUnknownAndInvalidPricesDoNotContribute() {
        bars.accept(tick("PLCE", START, trade(null, "10"), trade("0", "10"), trade("-1", "10"),
                trade("1", "10"), trade("2", null), trade("3", "-5")));
        TenSecondBar bar = bars.currentBar("PLCE").orElseThrow();
        assertEquals(3, bar.tradeCount());
        assertEquals(3, bars.stats().invalidTrades());
        assertFalse(bar.volumeComplete());
        assertNull(bar.volume()); assertNull(bar.vwap());
        bars.accept(tick("ZERO", START, trade("2", "0")));
        assertTrue(bars.currentBar("ZERO").orElseThrow().volumeComplete());
        assertEquals(BigDecimal.ZERO, bars.currentBar("ZERO").orElseThrow().volume());
        assertNull(bars.currentBar("ZERO").orElseThrow().vwap());
    }

    @Test void quotesSnapshotsAndProviderTimesDoNotAffectBars() {
        bars.accept(new MarketDataEvent("WEBULL", "QUOTE", "PLCE", null, "9999999999999", START,
                new QuoteData(null, "RTH", List.of(new PriceLevel(new BigDecimal("9"), BigDecimal.ONE)), List.of())));
        bars.accept(new MarketDataEvent("WEBULL", "SNAPSHOT", "PLCE", null, null, START,
                new TickData(List.of(trade("8", "100000")))));
        assertTrue(bars.currentBar("PLCE").isEmpty());
        bars.accept(tick("PLCE", START, trade("1.123456789123456789", "0.5")));
        assertEquals(new BigDecimal("1.123456789123456789"), bars.currentBar("PLCE").orElseThrow().vwap());
    }

    @Test void boundsHistoryAndEvictsIdleSymbolsWithoutDroppingActiveBars() {
        var small = new TenSecondBarAggregator(CHICAGO, 2, 2);
        small.accept(tick("A", START, trade("1", "1")));
        small.accept(tick("B", START, trade("2", "1")));
        small.accept(tick("C", START, trade("3", "1")));
        assertEquals(1, small.stats().capacityRejectedTrades());
        assertEquals(2, small.stats().activeBars());
        small.advanceTo(START.plusSeconds(10));
        small.completedBars("A"); // reading does not change eviction order
        small.accept(tick("C", START.plusSeconds(10), trade("3", "1")));
        assertTrue(small.completedBars("A").isEmpty());
        assertEquals(1, small.completedBars("B").size());
        small.accept(tick("C", START.plusSeconds(20), trade("4", "1")));
        small.accept(tick("C", START.plusSeconds(30), trade("5", "1")));
        small.advanceTo(START.plusSeconds(40));
        var history = small.completedBars("C");
        assertEquals(2, history.size());
        assertEquals(START.plusSeconds(20), history.getFirst().start().toInstant());
        assertThrows(UnsupportedOperationException.class, history::clear);
        assertEquals(5, small.stats().completedTotal());
    }

    @Test void repeatedChicagoHourKeepsDistinctInstantsAndOffsets() {
        Instant first = Instant.parse("2026-11-01T06:30:01Z");
        Instant second = first.plusSeconds(3600);
        bars.accept(tick("PLCE", first, trade("1", "1")));
        bars.accept(tick("PLCE", second, trade("2", "1")));
        bars.advanceTo(second.plusSeconds(10));
        var history = bars.completedBars("PLCE");
        assertEquals(history.getFirst().start().toLocalDateTime(), history.getLast().start().toLocalDateTime());
        assertEquals(ZoneOffset.ofHours(-5), history.getFirst().start().getOffset());
        assertEquals(ZoneOffset.ofHours(-6), history.getLast().start().getOffset());
        assertNotEquals(history.getFirst().start().toInstant(), history.getLast().start().toInstant());
    }

    @Test void validatesCapacitiesAndInvalidEnvelopeWithoutLosingNextTrade() {
        assertThrows(IllegalArgumentException.class, () -> new TenSecondBarAggregator(CHICAGO, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TenSecondBarAggregator(CHICAGO, 1, 1001));
        bars.accept(tick(" ", START, trade("1", "1")));
        bars.accept(tick("PLCE", null, trade("1", "1")));
        bars.accept(tick("PLCE", START, trade("1", "1")));
        assertEquals(2, bars.stats().invalidTrades());
        assertEquals(1, bars.currentBar("PLCE").orElseThrow().tradeCount());
    }

    static MarketDataEvent tick(String symbol, Instant time, Trade... trades) {
        return new MarketDataEvent("WEBULL", "TICK", symbol, "id", "uninterpreted", time, new TickData(List.of(trades)));
    }

    static Trade trade(String price, String volume) {
        return new Trade("15:00:00", price == null ? null : new BigDecimal(price),
                volume == null ? null : new BigDecimal(volume), "N", "RTH");
    }
}
