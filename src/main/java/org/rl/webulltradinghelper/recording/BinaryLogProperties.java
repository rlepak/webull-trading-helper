package org.rl.webulltradinghelper.recording;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("binary-log")
public record BinaryLogProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("data") Path directory,
        @DefaultValue("10000") int queueCapacity,
        @DefaultValue("1000") long flushIntervalMs) {
    public BinaryLogProperties {
        if (directory == null || queueCapacity < 1 || queueCapacity > 100_000
                || flushIntervalMs < 1 || flushIntervalMs > 60_000) {
            throw new IllegalArgumentException("Invalid binary recording configuration");
        }
    }
}
