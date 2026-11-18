package org.rl.webulltradinghelper.dashboard;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.rl.webulltradinghelper.aggregation.MarketAggregationService;
import org.rl.webulltradinghelper.webull.*;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    public record Row(String symbol, BigDecimal price, BigDecimal gainPercent, BigDecimal sessionVolume,
                      BigDecimal currentVolume, long tradeCount, BigDecimal baselineVolume,
                      BigDecimal volumeMultiplier, boolean highlighted, String status, Long ageSeconds) {}
    public record View(Instant asOf, String zone, GainersSession session, boolean connected,
                       Instant rankingUpdatedAt, boolean rankingStale, boolean aggregationEnabled,
                       BigDecimal threshold, int baselineBars, int staleSeconds, List<Row> rows) {}
    private static final class State {
        final Instant eligibleFrom;
        final long membership;
        Instant updatedAt;
        BigDecimal price, sessionVolume;
        Instant highlightUntil = Instant.MIN;
        long lastTriggeredWindow = Long.MIN_VALUE;
        State(Instant now, long membership) {
            this.membership = membership;
            eligibleFrom = Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), 10) * 10 + 10);
        }
    }
    private final Clock clock;
    private final ZoneId zone;
    private final SymbolSelection selection;
    private final TopGainersPoller poller;
    private final WebullStreamClient stream;
    private final MarketAggregationService bars;
    private final DashboardProperties settings;
    private final Map<String, State> states = new HashMap<>();
    private String period;
    private long generation = -1;

    public DashboardService(Clock clock, TopGainersProperties gainers, SymbolSelection selection,
                            TopGainersPoller poller, WebullStreamClient stream,
                            MarketAggregationService bars, DashboardProperties settings) {
        this.clock = clock; this.zone = ZoneId.of(gainers.zone()); this.selection = selection;
        this.poller = poller; this.stream = stream; this.bars = bars; this.settings = settings;
    }

    private String period(Instant time) {
        return time.atZone(zone).toLocalDate() + "/" + GainersSession.at(time, zone);
    }
    private void reconcile(Instant now) {
        String next = period(now);
        if (!next.equals(period) || generation != stream.connectionGeneration() || !stream.isConnected()) {
            states.clear(); period = next; generation = stream.connectionGeneration();
        }
        states.keySet().retainAll(selection.symbols());
        states.entrySet().removeIf(entry -> entry.getValue().membership != selection.membership(entry.getKey()));
    }

    @EventListener
    public synchronized void accept(MarketDataEvent event) {
        Instant now = clock.instant();
        reconcile(now);
        if (!stream.isConnected() || !selection.symbols().contains(event.symbol())
                || !period(event.receivedAt()).equals(period) || event.receivedAt().isAfter(now)) return;
        var state = states.computeIfAbsent(event.symbol(), key -> new State(event.receivedAt(), selection.membership(key)));
        if (state.updatedAt != null && event.receivedAt().isBefore(state.updatedAt)) return;
        state.updatedAt = event.receivedAt();
        var session = GainersSession.at(now, zone);
        String expected = session == GainersSession.PRE_MARKET ? "PRE" : session == GainersSession.DAY_1 ? "RTH" : "ATH";
        if (event.payload() instanceof MarketDataEvent.TickData ticks) {
            for (var trade : ticks.trades()) {
                if (expected.equals(trade.tradingSession()) && positive(trade.price())) state.price = trade.price();
            }
        } else if (event.payload() instanceof MarketDataEvent.SnapshotData snapshot
                && expected.equals(snapshot.tradingSession())) {
            var price = session == GainersSession.DAY_1 ? snapshot.price() : snapshot.extendHourLastPrice();
            if (positive(price)) state.price = price;
            var volume = session == GainersSession.DAY_1 ? snapshot.volume() : snapshot.extendHourVolume();
            state.sessionVolume = volume != null && volume.signum() >= 0 ? volume : null;
        }
        detect(event.symbol(), state, now);
    }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }

    private VolumeSurge.Result detect(String symbol, State state, Instant now) {
        var result = VolumeSurge.evaluate(bars.completedBars(symbol), bars.currentBar(symbol).orElse(null),
                now, state.eligibleFrom, settings);
        long window = Math.floorDiv(now.getEpochSecond(), 10);
        if (bars.isEnabled() && result.triggered() && state.lastTriggeredWindow != window) {
            state.lastTriggeredWindow = window;
            state.highlightUntil = now.plusSeconds(settings.highlightSeconds());
        }
        return result;
    }
    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public synchronized void refresh() { snapshot(); }

    public synchronized View snapshot() {
        var now = clock.instant();
        reconcile(now);
        var rankingAt = poller.lastRankingRequestAt();
        boolean currentRanking = rankingAt != null && period(rankingAt).equals(period);
        boolean rankingStale = !currentRanking || poller.lastSuccessAt() == null
                || Duration.between(poller.lastSuccessAt(), now).getSeconds() > settings.staleSeconds();
        List<Row> rows = new ArrayList<>();
        if (currentRanking) for (var stock : selection.ranking()) {
            var state = states.get(stock.symbol());
            Long age = state == null || state.updatedAt == null ? null : Math.max(0, Duration.between(state.updatedAt, now).getSeconds());
            boolean stale = age == null || age > settings.staleSeconds();
            var result = state == null ? new VolumeSurge.Result(null, null, false, "Warming up") : detect(stock.symbol(), state, now);
            var current = bars.currentBar(stock.symbol()).filter(b -> b.start().toInstant().getEpochSecond()
                    == Math.floorDiv(now.getEpochSecond(), 10) * 10).orElse(null);
            boolean highlight = stream.isConnected() && !stale && !rankingStale && bars.isEnabled()
                    && state != null && now.isBefore(state.highlightUntil)
                    && !"Volume unavailable".equals(result.status());
            String status = !stream.isConnected() ? "Disconnected" : stale ? "Waiting for data"
                    : !bars.isEnabled() ? "Aggregation disabled" : highlight ? "Volume surge" : result.status();
            rows.add(new Row(stock.symbol(), state != null && state.price != null ? state.price : stock.price(),
                    stock.changeRatio().multiply(BigDecimal.valueOf(100)), state == null ? null : state.sessionVolume,
                    state == null ? null : current == null ? BigDecimal.ZERO : current.volume(),
                    state == null || current == null ? 0 : current.tradeCount(), result.baseline(), result.multiplier(),
                    highlight, status, age));
        }
        return new View(now, zone.getId(), GainersSession.at(now, zone), stream.isConnected(), poller.lastSuccessAt(),
                rankingStale, bars.isEnabled(), settings.volumeMultiplier(), settings.baselineBars(), settings.staleSeconds(), List.copyOf(rows));
    }
}
