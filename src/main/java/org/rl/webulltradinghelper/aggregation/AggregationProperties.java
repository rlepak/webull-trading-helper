package org.rl.webulltradinghelper.aggregation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("aggregation")
public record AggregationProperties(@DefaultValue("true") boolean enabled,
        @DefaultValue("360") int historyPerSymbol, @DefaultValue("100") int maxSymbols) {
    public AggregationProperties {
        if (historyPerSymbol < 1 || historyPerSymbol > 3600 || maxSymbols < 1 || maxSymbols > 1000) {
            throw new IllegalArgumentException("Aggregation retention outside supported bounds");
        }
    }
}
