package org.rl.webulltradinghelper.webull;

import com.webull.openapi.data.quotes.subscribe.message.MarketData;
import java.time.Clock;
import org.rl.webulltradinghelper.aggregation.MarketAggregationService;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MarketDataLogger {
    private static final Logger log = LoggerFactory.getLogger(MarketDataLogger.class);
    private final java.util.concurrent.atomic.AtomicLong accepted = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong rejected = new java.util.concurrent.atomic.AtomicLong();
    private volatile java.time.Instant lastReceivedAt;
    private final MarketDataMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final org.rl.webulltradinghelper.recording.BinaryLogWriter recorder;
    private final MarketAggregationService aggregation;

    public MarketDataLogger(MarketDataMapper mapper, ObjectMapper json, Clock clock,
            org.rl.webulltradinghelper.recording.BinaryLogWriter recorder, MarketAggregationService aggregation) {
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
        this.recorder = recorder;
        this.aggregation = aggregation;
    }

    public long drainAcceptedCount() { return accepted.getAndSet(0); }
    public long drainRejectedCount() { return rejected.getAndSet(0); }
    public java.time.Instant lastReceivedAt() { return lastReceivedAt; }

    public void receive(MarketData message) {
        // Serialize timestamp capture/recording/aggregation against timer finalization.
        // Otherwise a callback captured just before a boundary could be incorrectly counted late.
        synchronized (aggregation) {
            receiveUnderAggregationLock(message);
        }
    }

    private void receiveUnderAggregationLock(MarketData message) {
        try {
            var event = mapper.map(message, clock.instant());
            lastReceivedAt = event.receivedAt();
            accepted.incrementAndGet();
            recorder.record(event);
            aggregation.accept(event);
            if (!log.isDebugEnabled()) return;
            ObjectNode displayed = json.valueToTree(event);
            displayed.put("receivedAt", event.receivedAt().atZone(clock.getZone()).toOffsetDateTime().toString());
            log.debug("Webull market data {}", json.writeValueAsString(displayed));
        } catch (RuntimeException error) {
            rejected.incrementAndGet();
            // Never print raw inbound data or exception text: both may contain sensitive values.
            log.warn("Webull market event rejected category={}", error.getClass().getSimpleName());
        }
    }
}
