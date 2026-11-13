package org.rl.webulltradinghelper.webull;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;

@org.springframework.scheduling.annotation.EnableScheduling
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({WebullProperties.class, TopGainersProperties.class,
        org.rl.webulltradinghelper.recording.BinaryLogProperties.class,
        org.rl.webulltradinghelper.aggregation.AggregationProperties.class})
public class WebullConfiguration {
    @Bean
    Clock webullClock(@Value("${app.time-zone:America/Chicago}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
