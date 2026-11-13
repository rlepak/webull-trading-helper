package org.rl.webulltradinghelper.webull;

import com.webull.openapi.core.common.dict.SubscribeType;
import com.webull.openapi.data.quotes.domain.*;
import com.webull.openapi.data.quotes.subscribe.message.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketDataMapperTest {
    private final MarketDataMapper mapper = new MarketDataMapper();
    private final Instant now = Instant.parse("2026-11-10T16:00:00Z");

    static MarketData message(QuotesBasic payload, SubscribeType type) {
        payload.setSymbol("AAPL");
        payload.setInstrumentId("test-instrument");
        return new MarketData(new Metadata(type), new QuotesPublish<>("raw-provider-timestamp", payload));
    }

    @Test void mapsQuotesWithPrecisionAndMissingValues() {
        Quote quote = new Quote();
        AskBid bid = new AskBid();
        bid.setPrice("123.4567890123456789");
        quote.setBids(List.of(bid));
        quote.setQuoteTime(123456789L);
        var event = mapper.map(message(quote, SubscribeType.QUOTE), now);
        QuoteData data = (QuoteData) event.payload();
        assertEquals(new BigDecimal("123.4567890123456789"), data.bids().getFirst().price());
        assertNull(data.bids().getFirst().size());
        assertTrue(data.asks().isEmpty());
        assertEquals(123456789L, data.quoteTime());
        assertEquals("raw-provider-timestamp", event.providerTimestamp());
        assertEquals(now, event.receivedAt());
        assertEquals("WEBULL", event.source());
        assertThrows(UnsupportedOperationException.class, () -> data.bids().clear());
    }

    @Test void mapsSnapshotIncludingExtendedHoursWithoutInventingTimestamps() {
        Snapshot snapshot = new Snapshot();
        snapshot.setPrice("101.25");
        snapshot.setExtendHourLastPrice("102.50");
        snapshot.setExtendHourLastTradeTime(42L);
        snapshot.setOvnVolume("17");
        var data = (SnapshotData) mapper.map(message(snapshot, SubscribeType.SNAPSHOT), now).payload();
        assertEquals(new BigDecimal("101.25"), data.price());
        assertEquals(new BigDecimal("102.50"), data.extendHourLastPrice());
        assertEquals(42L, data.extendHourLastTradeTime());
        assertEquals(new BigDecimal("17"), data.ovnVolume());
        assertNull(data.lastTradeTime());
        assertNull(data.volume());
    }

    @Test void preservesEveryTickInBatch() {
        Tick tick = new Tick();
        TickRecord first = new TickRecord();
        first.setTime("1234567890123456789"); first.setPrice("10.05"); first.setVolume("2.5");
        first.setSide("B"); first.setTradingSession("PRE");
        TickRecord second = new TickRecord();
        second.setPrice("10.06");
        tick.setResult(List.of(first, second));
        var data = (TickData) mapper.map(message(tick, SubscribeType.TICK), now).payload();
        assertEquals(2, data.trades().size());
        assertEquals("1234567890123456789", data.trades().getFirst().time());
        assertEquals(new BigDecimal("2.5"), data.trades().getFirst().volume());
        assertEquals("PRE", data.trades().getFirst().tradingSession());
    }

    @Test void rejectsMalformedDecimalsAndInvalidSymbols() {
        Snapshot snapshot = new Snapshot(); snapshot.setPrice("not-a-number");
        var message = message(snapshot, SubscribeType.SNAPSHOT);
        assertThrows(NumberFormatException.class, () -> mapper.map(message, now));
        snapshot.setPrice(null); snapshot.setSymbol("\nsecret");
        assertThrows(IllegalArgumentException.class, () -> mapper.map(message, now));
    }
}
