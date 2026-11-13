package org.rl.webulltradinghelper.webull;

import com.webull.openapi.data.quotes.api.subsribe.IMarketStreamingClient;
import com.webull.openapi.data.quotes.subscribe.IDataStreamingClient;
import java.util.Set;

/** HTTP subscription management and MQTT transport share exactly one session identifier. */
public record WebullStreamSession(IDataStreamingClient client, IMarketStreamingClient subscriptions, String sessionId) {
    void add(Set<String> symbols, String type) {
        subscriptions.subscribe(sessionId, symbols, "US_STOCK", Set.of(type), "1", false);
    }
    void remove(Set<String> symbols, String type) {
        subscriptions.unsubscribe(sessionId, symbols, "US_STOCK", Set.of(type), false);
    }
    @Override public String toString() { return "WebullStreamSession[details=REDACTED]"; }
}
