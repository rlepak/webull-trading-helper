package org.rl.webulltradinghelper.aggregation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent.TickData;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent.Trade;

/** No wall-clock or I/O dependencies: callers supply recorded receive times and advancement. */
public final class TenSecondBarAggregator {
    private final ZoneId zone;
    private final int historyPerSymbol;
    private final int maxSymbols;
    // Insertion order is explicitly refreshed only when a trade is added, never by reads.
    private final LinkedHashMap<String, SymbolBars> symbols = new LinkedHashMap<>();
    private Instant boundary;
    private long completedTotal, invalidTrades, lateTrades, capacityRejectedTrades;

    public TenSecondBarAggregator(ZoneId zone, int historyPerSymbol, int maxSymbols) {
        this.zone = Objects.requireNonNull(zone);
        if (historyPerSymbol < 1 || historyPerSymbol > 3600 || maxSymbols < 1 || maxSymbols > 1000) {
            throw new IllegalArgumentException("Aggregation retention outside supported bounds");
        }
        this.historyPerSymbol = historyPerSymbol;
        this.maxSymbols = maxSymbols;
    }

    public synchronized void accept(MarketDataEvent event) {
        if (event == null || !"TICK".equals(event.type()) || !(event.payload() instanceof TickData ticks)) return;
        if (event.symbol() == null || event.symbol().isBlank() || event.receivedAt() == null) {
            invalidTrades += ticks.trades().size();
            return;
        }
        Instant start = bucket(event.receivedAt());
        advanceTo(event.receivedAt());
        for (Trade trade : ticks.trades()) {
            if (trade.price() == null || trade.price().signum() <= 0) {
                invalidTrades++;
                continue;
            }
            if (start.isBefore(boundary)) {
                lateTrades++;
                continue;
            }
            SymbolBars state = symbols.get(event.symbol());
            if (state == null) {
                if (!makeRoom()) {
                    capacityRejectedTrades++;
                    continue;
                }
                state = new SymbolBars();
            }
            if (state.current == null) state.current = new MutableBar(event.symbol(), start);
            state.current.add(trade, event.receivedAt());
            symbols.remove(event.symbol());
            symbols.put(event.symbol(), state);
        }
    }

    /** Finalize only elapsed windows; backward advancement cannot reopen them. */
    public synchronized void advanceTo(Instant time) {
        Instant next = bucket(Objects.requireNonNull(time));
        if (boundary != null && !next.isAfter(boundary)) return;
        boundary = next;
        for (SymbolBars state : symbols.values()) {
            if (state.current != null && !state.current.start.plusSeconds(10).isAfter(boundary)) {
                state.history.addLast(state.current.snapshot(true));
                state.current = null;
                completedTotal++;
                while (state.history.size() > historyPerSymbol) state.history.removeFirst();
            }
        }
    }

    public synchronized List<TenSecondBar> completedBars(String symbol) {
        SymbolBars state = symbols.get(symbol);
        return state == null ? List.of() : List.copyOf(state.history);
    }

    public synchronized Optional<TenSecondBar> currentBar(String symbol) {
        SymbolBars state = symbols.get(symbol);
        return state == null || state.current == null ? Optional.empty() : Optional.of(state.current.snapshot(false));
    }

    public synchronized Stats stats() {
        int active = (int) symbols.values().stream().filter(s -> s.current != null).count();
        return new Stats(symbols.size(), active, completedTotal, invalidTrades, lateTrades, capacityRejectedTrades);
    }

    public record Stats(int retainedSymbols, int activeBars, long completedTotal,
                        long invalidTrades, long lateTrades, long capacityRejectedTrades) {}

    private boolean makeRoom() {
        if (symbols.size() < maxSymbols) return true;
        var iterator = symbols.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().current == null) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private static Instant bucket(Instant time) {
        return Instant.ofEpochSecond(Math.floorDiv(time.getEpochSecond(), 10L) * 10L);
    }

    private final class SymbolBars {
        private final ArrayDeque<TenSecondBar> history = new ArrayDeque<>();
        private MutableBar current;
    }

    private final class MutableBar {
        private final String symbol;
        private final Instant start;
        private Instant firstTime, lastTime;
        private BigDecimal open, high, low, close;
        private BigDecimal volume = BigDecimal.ZERO, priceVolume = BigDecimal.ZERO;
        private boolean volumeComplete = true;
        private long tradeCount;

        private MutableBar(String symbol, Instant start) {
            this.symbol = symbol;
            this.start = start;
        }

        private void add(Trade trade, Instant time) {
            BigDecimal price = trade.price();
            if (firstTime == null || time.isBefore(firstTime)) { firstTime = time; open = price; }
            if (lastTime == null || !time.isBefore(lastTime)) { lastTime = time; close = price; }
            high = high == null ? price : high.max(price);
            low = low == null ? price : low.min(price);
            if (trade.volume() == null || trade.volume().signum() < 0) volumeComplete = false;
            else {
                volume = volume.add(trade.volume());
                priceVolume = priceVolume.add(price.multiply(trade.volume()));
            }
            tradeCount++;
        }

        private TenSecondBar snapshot(boolean completed) {
            BigDecimal vwap = volumeComplete && volume.signum() > 0
                    ? priceVolume.divide(volume, MathContext.DECIMAL128) : null;
            return new TenSecondBar(symbol, start.atZone(zone).toOffsetDateTime(),
                    start.plusSeconds(10).atZone(zone).toOffsetDateTime(), TenSecondBar.TimeBasis.RECEIVE_TIME,
                    completed, open, high, low, close, volumeComplete ? volume : null, vwap, volumeComplete, tradeCount);
        }
    }
}
