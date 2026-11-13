package org.rl.webulltradinghelper.aggregation;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.rl.webulltradinghelper.aggregation.TenSecondBarAggregatorTest.*;

class MarketAggregationServiceTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfiguration {}

    @Test void springSchedulerFinalizesIdleBarsWithoutAnotherMarketEvent() {
        Instant start = Instant.parse("2026-11-12T20:00:00Z");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SchedulingConfiguration.class);
            context.registerBean(Clock.class, () -> Clock.fixed(start.plusSeconds(10), ZoneId.of("America/Chicago")));
            context.registerBean(AggregationProperties.class, () -> new AggregationProperties(true, 360, 100));
            context.registerBean(MarketAggregationService.class);
            context.refresh();
            var service = context.getBean(MarketAggregationService.class);
            service.accept(tick("PLCE", start.plusSeconds(1), trade("1.25", "50")));
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                while (service.stats().completedTotal() == 0) Thread.sleep(10);
            });
            assertEquals(1, service.completedBars("PLCE").size());
            assertTrue(service.currentBar("PLCE").isEmpty());
        }
    }

    @Test void timerClosesIdleWindowUsingSharedClockAndDisabledServiceDoesNoWork() {
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Chicago"));
        Instant start = Instant.parse("2026-11-12T20:00:00Z");
        var enabled = new MarketAggregationService(clock, new AggregationProperties(true, 360, 100));
        var disabled = new MarketAggregationService(clock, new AggregationProperties(false, 360, 100));
        var event = tick("PLCE", start.plusSeconds(1), trade("1.25", "50"));
        enabled.accept(event); disabled.accept(event);
        when(clock.instant()).thenReturn(start.plusSeconds(10));
        enabled.closeElapsedWindows(); disabled.closeElapsedWindows();
        assertTrue(enabled.currentBar("PLCE").isEmpty());
        assertEquals(1, enabled.completedBars("PLCE").size());
        assertEquals(1, enabled.stats().completedTotal());
        assertEquals(0, disabled.stats().retainedSymbols());
        assertTrue(disabled.completedBars("PLCE").isEmpty());
        verify(clock, times(1)).instant();
    }

    @Test void recordedReceiveTimesProduceSameBarsWithAndWithoutIntermediateIdleTicks() {
        Instant start = Instant.parse("2026-11-12T20:00:00Z");
        var live = new TenSecondBarAggregator(ZoneId.of("America/Chicago"), 360, 100);
        var replay = new TenSecondBarAggregator(ZoneId.of("America/Chicago"), 360, 100);
        for (int second : new int[]{1, 2, 8, 10, 12, 25, 26}) {
            var event = tick("PLCE", start.plusSeconds(second), trade(Integer.toString(second), "10"));
            live.advanceTo(event.receivedAt());
            live.accept(event);
            replay.accept(event);
        }
        live.advanceTo(start.plusSeconds(30));
        replay.advanceTo(start.plusSeconds(30));
        assertEquals(live.completedBars("PLCE"), replay.completedBars("PLCE"));
        assertEquals(3, replay.stats().completedTotal());
    }

    @Test void invalidConfigurationFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new AggregationProperties(true, -1, 100));
        assertThrows(IllegalArgumentException.class, () -> new AggregationProperties(true, 3601, 100));
        assertThrows(IllegalArgumentException.class, () -> new AggregationProperties(true, 360, 0));
    }
}
