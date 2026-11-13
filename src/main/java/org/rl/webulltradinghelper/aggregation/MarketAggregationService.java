package org.rl.webulltradinghelper.aggregation;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MarketAggregationService {
    private final Clock clock;
    private final boolean enabled;
    private final TenSecondBarAggregator aggregator;

    public MarketAggregationService(Clock clock, AggregationProperties properties) {
        this.clock = clock;
        this.enabled = properties.enabled();
        this.aggregator = new TenSecondBarAggregator(clock.getZone(), properties.historyPerSymbol(), properties.maxSymbols());
    }

    public synchronized void accept(MarketDataEvent event) {
        if (enabled) aggregator.accept(event);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public synchronized void closeElapsedWindows() {
        if (enabled) aggregator.advanceTo(clock.instant());
    }

    public boolean isEnabled() { return enabled; }
    public List<TenSecondBar> completedBars(String symbol) { return aggregator.completedBars(symbol); }
    public Optional<TenSecondBar> currentBar(String symbol) { return aggregator.currentBar(symbol); }
    public TenSecondBarAggregator.Stats stats() { return aggregator.stats(); }
}
