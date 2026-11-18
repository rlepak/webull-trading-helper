package org.rl.webulltradinghelper.dashboard;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("dashboard")
public record DashboardProperties(@DefaultValue("3") BigDecimal volumeMultiplier,
                                  @DefaultValue("3") int baselineBars,
                                  @DefaultValue("10") int highlightSeconds,
                                  @DefaultValue("15") int staleSeconds) {
    public DashboardProperties {
        if (volumeMultiplier == null || volumeMultiplier.compareTo(BigDecimal.ONE) <= 0
                || baselineBars < 1 || baselineBars > 60 || highlightSeconds < 1 || highlightSeconds > 300
                || staleSeconds < 1 || staleSeconds > 300)
            throw new IllegalArgumentException("Invalid dashboard settings");
    }
}
