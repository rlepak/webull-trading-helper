package org.rl.webulltradinghelper.webull;

import com.webull.openapi.data.quotes.domain.Snapshot;
import com.webull.openapi.data.quotes.domain.Tick;
import com.webull.openapi.data.quotes.subscribe.message.MarketData;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SymbolSelection {
    private static final Logger log = LoggerFactory.getLogger(SymbolSelection.class);
    private final TopGainersProperties settings;
    private volatile Set<String> symbols;

    public SymbolSelection(TopGainersProperties settings, WebullProperties properties) {
        this.settings = settings;
        symbols = settings.enabled() ? Set.of() : Set.copyOf(properties.streaming().symbols());
    }

    public Set<String> symbols() { return symbols; }

    public synchronized void replace(List<GainerStock> ranking) {
        Set<String> next = new LinkedHashSet<>();
        for (GainerStock row : ranking) {
            if (row == null || row.symbol() == null || !settings.eligiblePrice(row.price())
                    || row.changeRatio() == null || row.changeRatio().signum() <= 0) continue;
            String symbol = row.symbol().trim().toUpperCase(Locale.ROOT);
            if (symbol.matches("[A-Z0-9][A-Z0-9./-]{0,19}")) next.add(symbol);
            if (next.size() == 10) break; // Top ten eligible stocks in provider ranking order.
        }
        if (!next.equals(symbols)) {
            log.debug("Webull eligible gainers changed previous={} current={} symbols={}", symbols.size(), next.size(), next);
            symbols = Collections.unmodifiableSet(next);
        }
    }

    /** SDK callback: no network I/O; the socket worker reconciles this desired set. */
    public synchronized boolean accept(MarketData message) {
        if (message == null || message.getData() == null) return false;
        String symbol = message.getData().getSymbol();
        if (!symbols.contains(symbol)) return false;
        if (!settings.enabled()) return true;
        String price = null;
        if (message.getData() instanceof Snapshot snapshot) {
            price = snapshot.getPrice();
            if (("PRE".equals(snapshot.getTradingSession()) || "ATH".equals(snapshot.getTradingSession()))
                    && snapshot.getExtendHourLastPrice() != null) price = snapshot.getExtendHourLastPrice();
            if ("OVN".equals(snapshot.getTradingSession()) && snapshot.getOvnPrice() != null) price = snapshot.getOvnPrice();
        } else if (message.getData() instanceof Tick tick && tick.getResult() != null && !tick.getResult().isEmpty()) {
            var trade = tick.getResult().getLast();
            if (trade != null) price = trade.getPrice();
        }
        if (price == null) return true;
        try {
            if (!settings.eligiblePrice(new BigDecimal(price))) {
                var next = new LinkedHashSet<>(symbols);
                next.remove(symbol);
                symbols = Collections.unmodifiableSet(next);
                log.debug("Webull symbol removed after price left range symbol={}", symbol);
                return false;
            }
        } catch (NumberFormatException invalid) { /* Mapper reports malformed events safely. */ }
        return true;
    }
}
