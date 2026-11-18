package org.rl.webulltradinghelper.webull;

import com.webull.openapi.core.http.exception.HttpServerException;
import com.webull.openapi.core.common.dict.SubscribeType;
import com.webull.openapi.data.quotes.domain.Snapshot;
import com.webull.openapi.data.quotes.domain.Tick;
import com.webull.openapi.data.quotes.domain.TickRecord;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TopGainersTest {
    static TopGainersProperties settings(boolean enabled) {
        return new TopGainersProperties(enabled, 5000, "America/Chicago", new BigDecimal("0.10"), new BigDecimal("10.00"));
    }
    static GainerStock stock(String symbol, String price) {
        return new GainerStock(symbol, price == null ? null : new BigDecimal(price), BigDecimal.ONE);
    }

    @Test void selectsExactBoundariesInWinterSummerAndWeekends() {
        ZoneId zone = ZoneId.of("America/Chicago");
        for (String date : List.of("2026-11-12", "2026-07-12", "2026-03-08", "2026-11-01")) {
            for (var item : List.of(
                    new String[]{"02:59:59", "AFTER_MARKET"}, new String[]{"03:00:00", "PRE_MARKET"},
                    new String[]{"08:29:59", "PRE_MARKET"}, new String[]{"08:30:00", "DAY_1"},
                    new String[]{"14:59:59", "DAY_1"}, new String[]{"15:00:00", "AFTER_MARKET"},
                    new String[]{"23:59:59", "AFTER_MARKET"})) {
                LocalDateTime local = LocalDateTime.parse(date + "T" + item[0]);
                if (zone.getRules().getValidOffsets(local).isEmpty()) continue; // Spring DST gap.
                Instant instant = local.atZone(zone).toInstant();
                assertEquals(item[1], GainersSession.at(instant, zone).name(), date + " " + item[0]);
            }
        }
    }

    @Test void springClockJumpSelectsPremarketAtThree() {
        ZoneId zone = ZoneId.of("America/Chicago");
        assertEquals(GainersSession.AFTER_MARKET, GainersSession.at(Instant.parse("2026-03-08T07:59:59Z"), zone));
        assertEquals(GainersSession.PRE_MARKET, GainersSession.at(Instant.parse("2026-03-08T08:00:00Z"), zone));
    }

    @Test void selectsInclusiveBoundsAndExcludesInvalidRowsWithoutManualFallback() {
        var selection = new SymbolSelection(settings(true), WebullPropertiesTest.properties(true));
        assertTrue(selection.symbols().isEmpty());
        selection.replace(List.of(stock("LOW", "0.0999"), stock("MIN", "0.10"), stock("MAX", "10.00"),
                stock("HIGH", "10.0001"), stock("UNKNOWN", null), stock(" min ", "0.10"),
                stock("bad\nsymbol", "1"), new GainerStock("LOSER", BigDecimal.ONE, BigDecimal.ONE.negate()),
                new GainerStock("NOCHANGE", BigDecimal.ONE, null)));
        assertEquals(Set.of("MIN", "MAX"), selection.symbols());
        selection.replace(List.of());
        assertTrue(selection.symbols().isEmpty());
    }

    @Test void capsSelectionAtHighestRankedTenEligibleSymbols() {
        var selection = new SymbolSelection(settings(true), WebullPropertiesTest.properties(true));
        selection.replace(java.util.stream.IntStream.range(0, 170)
                .mapToObj(i -> stock("S" + i, i < 5 ? "11" : "1")).toList());
        assertEquals(10, selection.symbols().size());
        assertTrue(selection.symbols().contains("S5"));
        assertTrue(selection.symbols().contains("S14"));
        assertFalse(selection.symbols().contains("S15"));
    }

    @Test void livePriceExitsAndLaterRankingCanRequalify() {
        var selection = new SymbolSelection(settings(true), WebullPropertiesTest.properties(true));
        selection.replace(List.of(stock("AAPL", "9.99")));
        var snapshot = new Snapshot(); snapshot.setPrice("10.0001");
        assertFalse(selection.accept(MarketDataMapperTest.message(snapshot, SubscribeType.SNAPSHOT)));
        assertTrue(selection.symbols().isEmpty());
        selection.replace(List.of(stock("AAPL", "10.00")));
        snapshot.setPrice("10.00");
        assertTrue(selection.accept(MarketDataMapperTest.message(snapshot, SubscribeType.SNAPSHOT)));
        snapshot.setTradingSession("ATH"); snapshot.setExtendHourLastPrice("0.09");
        assertFalse(selection.accept(MarketDataMapperTest.message(snapshot, SubscribeType.SNAPSHOT)));
        selection.replace(List.of(stock("AAPL", "1")));
        var tick = new Tick(); var trade = new TickRecord(); trade.setPrice("0.099"); tick.setResult(List.of(trade));
        assertFalse(selection.accept(MarketDataMapperTest.message(tick, SubscribeType.TICK)));
    }

    @Test void successfulEmptyPollClearsSelectionButErrorsRetainItAndThrottleBacksOff() {
        var settings = settings(true);
        var props = WebullPropertiesTest.properties(true);
        var selection = new SymbolSelection(settings, props);
        var gateway = mock(TopGainersGateway.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneOffset.UTC);
        var poller = new TopGainersPoller(settings, props, gateway, selection, clock);
        when(gateway.fetch(GainersSession.DAY_1)).thenReturn(List.of(stock("ABC", "1")));
        assertEquals(5000, poller.pollOnce());
        assertEquals(Set.of("ABC"), selection.symbols());
        HttpServerException throttle = new HttpServerException("RATE_LIMIT", "private-response", 429, "test");
        when(gateway.fetch(GainersSession.DAY_1)).thenThrow(throttle);
        assertEquals(60_000, poller.pollOnce());
        assertEquals(120_000, poller.pollOnce());
        assertEquals(Set.of("ABC"), selection.symbols());
        doReturn(List.of()).when(gateway).fetch(GainersSession.DAY_1);
        assertEquals(5000, poller.pollOnce());
        assertTrue(selection.symbols().isEmpty());
    }

    @Test void disabledStreamingDoesNotPoll() {
        var props = WebullPropertiesTest.properties(false);
        var gateway = mock(TopGainersGateway.class);
        var poller = new TopGainersPoller(settings(true), props, gateway,
                new SymbolSelection(settings(true), props), Clock.systemUTC());
        poller.start(); poller.stop();
        verifyNoInteractions(gateway);
    }

    @Test void rankingRefreshRetainsNewOrderAndPricesWithoutMembershipChange() {
        var selection = new SymbolSelection(settings(true), WebullPropertiesTest.properties(true));
        selection.replace(List.of(stock("AAA", "1"), stock("BBB", "2")));
        selection.replace(List.of(stock("BBB", "3"), stock("AAA", "2")));
        assertEquals(List.of("BBB", "AAA"), selection.ranking().stream().map(GainerStock::symbol).toList());
        assertEquals(new BigDecimal("3"), selection.ranking().getFirst().price());
        assertEquals(Set.of("AAA", "BBB"), selection.symbols());
    }

    @Test void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TopGainersProperties(true, 100,
                "America/Chicago", new BigDecimal(".1"), BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class, () -> new TopGainersProperties(true, 5000,
                "America/Chicago", BigDecimal.TEN, BigDecimal.ONE));
    }
}
