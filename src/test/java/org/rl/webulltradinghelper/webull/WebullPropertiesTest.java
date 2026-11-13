package org.rl.webulltradinghelper.webull;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebullPropertiesTest {
    static WebullProperties properties(boolean enabled) {
        return new WebullProperties("test-key", "test-secret", "us", "api.webull.com",
                new WebullProperties.Streaming(enabled, "data-api.webull.com", 1883,
                        List.of(" aapl ", "AAPL"), List.of("QUOTE", "SNAPSHOT", "TICK"), 1000));
    }

    @Test void normalizesAndDeduplicatesSymbols() {
        assertEquals(List.of("AAPL"), properties(true).streaming().symbols());
    }

    @Test void rejectsMissingCredentialsWithoutPrintingValues() {
        var p = new WebullProperties("test-key", "", "us", "api.webull.com", properties(true).streaming());
        var error = assertThrows(IllegalArgumentException.class, p::validateCredentials);
        assertFalse(error.getMessage().contains("test-key"));
        assertFalse(properties(true).toString().contains("test-secret"));
        assertFalse(properties(true).toString().contains("test-key"));
    }

    @Test void rejectsUnsupportedTypesAndEmptySymbols() {
        assertThrows(IllegalArgumentException.class, () -> new WebullProperties.Streaming(true,
                "data-api.webull.com", 1883, List.of("AAPL"), List.of("ORDER"), 1000));
        assertThrows(IllegalArgumentException.class, () -> new WebullProperties.Streaming(true,
                "data-api.webull.com", 1883, List.of(), List.of("TICK"), 1000));
    }
}
