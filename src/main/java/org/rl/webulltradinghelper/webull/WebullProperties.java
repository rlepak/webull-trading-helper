package org.rl.webulltradinghelper.webull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("webull")
public record WebullProperties(String appKey, String appSecret,
        @DefaultValue("us") String regionId,
        @DefaultValue("api.webull.com") String endpoint,
        @DefaultValue Streaming streaming) {

    public record Streaming(@DefaultValue("false") boolean enabled,
            @DefaultValue("data-api.webull.com") String host,
            @DefaultValue("1883") int port,
            @DefaultValue("AAPL") List<String> symbols,
            @DefaultValue({"QUOTE", "SNAPSHOT", "TICK"}) List<String> types,
            @DefaultValue("10000") int timeoutMs) {
        public Streaming {
            symbols = normalize(symbols);
            types = normalize(types);
            if (host == null || !host.matches("[a-zA-Z0-9.-]+") || port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid Webull streaming host or port");
            }
            if (timeoutMs < 1000 || timeoutMs > 60000) {
                throw new IllegalArgumentException("Webull streaming timeout must be 1000..60000 milliseconds");
            }
            if (symbols.isEmpty() || symbols.size() > 50
                    || symbols.stream().anyMatch(s -> !s.matches("[A-Z0-9][A-Z0-9./-]{0,19}"))) {
                throw new IllegalArgumentException("Configure 1..50 valid Webull symbols");
            }
            if (types.isEmpty() || !List.of("QUOTE", "SNAPSHOT", "TICK").containsAll(types)) {
                throw new IllegalArgumentException("Webull types must be QUOTE, SNAPSHOT, or TICK");
            }
        }

        private static List<String> normalize(List<String> values) {
            if (values == null) return List.of();
            return List.copyOf(new LinkedHashSet<>(values.stream()
                    .map(s -> s == null ? "" : s.trim().toUpperCase(Locale.ROOT)).toList()));
        }
    }

    public void validateCredentials() {
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("Webull streaming requires webull.app-key and webull.app-secret");
        }
        if (!"us".equals(regionId) || endpoint == null || !endpoint.matches("[a-zA-Z0-9.-]+")) {
            throw new IllegalArgumentException("Configure a valid Webull HTTP host and US region");
        }
    }

    @Override
    public String toString() {
        return "WebullProperties[credentials=REDACTED]";
    }
}
