package org.rl.webulltradinghelper.dashboard;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rl.webulltradinghelper.aggregation.*;
import org.rl.webulltradinghelper.webull.*;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    Clock clock = mock(Clock.class);
    SymbolSelection selection = mock(SymbolSelection.class);
    TopGainersPoller poller = mock(TopGainersPoller.class);
    WebullStreamClient stream = mock(WebullStreamClient.class);
    MarketAggregationService bars;
    DashboardService dashboard;
    Instant origin = Instant.parse("2026-11-18T13:00:00Z");
    @BeforeEach void setup() {
        when(clock.getZone()).thenReturn(ZoneId.of("America/Chicago"));
        when(stream.isConnected()).thenReturn(true);
        when(selection.symbols()).thenReturn(java.util.Set.of("TEST"));
        when(selection.ranking()).thenReturn(List.of(new GainerStock("TEST", BigDecimal.ONE, new BigDecimal("0.25"))));
        when(poller.lastRankingRequestAt()).thenReturn(origin);
        bars = new MarketAggregationService(clock, new AggregationProperties(true,360,100));
        dashboard = new DashboardService(clock,new TopGainersProperties(true,5000,"America/Chicago",new BigDecimal("0.1"),BigDecimal.TEN),selection,poller,stream,bars,new DashboardProperties(new BigDecimal("3"),3,10,15));
        time(0);
    }
    void time(int seconds) { when(clock.instant()).thenReturn(origin.plusSeconds(seconds)); when(poller.lastSuccessAt()).thenReturn(origin.plusSeconds(seconds)); }
    void tick(int seconds, String volume) {
        time(seconds);
        var event = new MarketDataEvent("WEBULL","TICK","TEST","id",null,clock.instant(),new MarketDataEvent.TickData(List.of(new MarketDataEvent.Trade(null,new BigDecimal("1.23"),new BigDecimal(volume),null,"PRE"))));
        bars.accept(event); dashboard.accept(event);
    }
    void surge() { tick(1,"9000"); tick(11,"100"); tick(21,"100"); tick(31,"100"); tick(41,"300"); }
    @Test void liveSurgeExpiresTenSecondsAfterFirstTrigger() {
        surge();
        var row = dashboard.snapshot().rows().getFirst();
        assertThat(row.highlighted()).isTrue();
        assertThat(row.price()).isEqualByComparingTo("1.23");
        assertThat(row.gainPercent()).isEqualByComparingTo("25");
        assertThat(row.sessionVolume()).isNull();
        tick(49,"1"); assertThat(dashboard.snapshot().rows().getFirst().highlighted()).isTrue();
        time(50); assertThat(dashboard.snapshot().rows().getFirst().highlighted()).isTrue();
        time(51); assertThat(dashboard.snapshot().rows().getFirst().highlighted()).isFalse();
    }
    @Test void staleDisconnectedAndReconnectedFeedsCannotReuseSurge() {
        surge(); time(57); assertThat(dashboard.snapshot().rows().getFirst().highlighted()).isFalse();
        when(stream.isConnected()).thenReturn(false);
        assertThat(dashboard.snapshot().rows().getFirst().status()).isEqualTo("Disconnected");
        when(stream.isConnected()).thenReturn(true); when(stream.connectionGeneration()).thenReturn(2L);
        tick(58,"1000"); assertThat(dashboard.snapshot().rows().getFirst().highlighted()).isFalse();
    }
    @Test void sessionChangeWaitsForNewRankingAndRemovedSymbolsDisappear() {
        surge(); time(5400);
        assertThat(dashboard.snapshot().session()).isEqualTo(GainersSession.DAY_1);
        assertThat(dashboard.snapshot().rows()).isEmpty();
        time(45); when(selection.symbols()).thenReturn(java.util.Set.of()); when(selection.ranking()).thenReturn(List.of());
        assertThat(dashboard.snapshot().rows()).isEmpty();
    }
    @Test void rapidRemovalAndReentryCannotReusePriorBaseline() {
        surge();
        when(selection.membership("TEST")).thenReturn(2L);
        tick(42,"1000");
        assertThat(dashboard.snapshot().rows().getFirst().highlighted()).isFalse();
        assertThat(dashboard.snapshot().rows().getFirst().status()).isEqualTo("Warming up");
    }
    @Test void snapshotVolumeUsesMatchingSessionFields() {
        var snapshot = mock(MarketDataEvent.SnapshotData.class);
        when(snapshot.tradingSession()).thenReturn("PRE");
        when(snapshot.price()).thenReturn(new BigDecimal("1.10"));
        when(snapshot.extendHourLastPrice()).thenReturn(new BigDecimal("1.50"));
        when(snapshot.volume()).thenReturn(new BigDecimal("10000"));
        when(snapshot.extendHourVolume()).thenReturn(new BigDecimal("20000"));
        dashboard.accept(new MarketDataEvent("WEBULL","SNAPSHOT","TEST","id",null,clock.instant(),snapshot));
        var row = dashboard.snapshot().rows().getFirst();
        assertThat(row.price()).isEqualByComparingTo("1.50");
        assertThat(row.sessionVolume()).isEqualByComparingTo("20000");
        time(5400); when(poller.lastRankingRequestAt()).thenReturn(origin.plusSeconds(5400));
        when(snapshot.tradingSession()).thenReturn("RTH");
        dashboard.accept(new MarketDataEvent("WEBULL","SNAPSHOT","TEST","id",null,clock.instant(),snapshot));
        row = dashboard.snapshot().rows().getFirst();
        assertThat(row.price()).isEqualByComparingTo("1.10");
        assertThat(row.sessionVolume()).isEqualByComparingTo("10000");
        time(28800); when(poller.lastRankingRequestAt()).thenReturn(origin.plusSeconds(28800));
        when(snapshot.tradingSession()).thenReturn("ATH");
        dashboard.accept(new MarketDataEvent("WEBULL","SNAPSHOT","TEST","id",null,clock.instant(),snapshot));
        assertThat(dashboard.snapshot().rows().getFirst().sessionVolume()).isEqualByComparingTo("20000");
    }
    @Test void staleRankingSuppressesHighlightAndMidnightClearsRows() {
        surge();
        when(poller.lastSuccessAt()).thenReturn(origin);
        assertThat(dashboard.snapshot().rankingStale()).isTrue();
        assertThat(dashboard.snapshot().rows().getFirst().highlighted()).isFalse();
        time(86400);
        assertThat(dashboard.snapshot().rows()).isEmpty();
    }
    @Test void endpointDisablesCaching() {
        assertThat(new DashboardController(dashboard).snapshot().getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
