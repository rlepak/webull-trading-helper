package org.rl.webulltradinghelper.webull;

import java.math.BigDecimal;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("webull.top-gainers")
public record TopGainersProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("5000") long pollIntervalMs,
        @DefaultValue("America/Chicago") String zone,
        @DefaultValue("0.10") BigDecimal minPrice,
        @DefaultValue("10.00") BigDecimal maxPrice) {
    public TopGainersProperties {
        ZoneId.of(zone);
        if (pollIntervalMs < 5000 || minPrice == null || maxPrice == null
                || minPrice.signum() <= 0 || minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Invalid top-gainers interval or price bounds");
        }
    }

    public boolean eligiblePrice(BigDecimal price) {
        return price != null && price.compareTo(minPrice) >= 0 && price.compareTo(maxPrice) <= 0;
    }
}
