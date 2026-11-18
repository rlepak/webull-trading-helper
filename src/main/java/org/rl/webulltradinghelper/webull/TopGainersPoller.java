package org.rl.webulltradinghelper.webull;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class TopGainersPoller implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(TopGainersPoller.class);
    private final TopGainersProperties settings;
    private final WebullProperties webull;
    private final TopGainersGateway gateway;
    private final SymbolSelection selection;
    private final Clock clock;
    private volatile boolean running;
    private ScheduledExecutorService executor;
    private int failures;
    private volatile java.time.Instant lastSuccessAt;
    private volatile java.time.Instant lastRankingRequestAt;
    public java.time.Instant lastRankingRequestAt() { return lastRankingRequestAt; }
    public java.time.Instant lastSuccessAt() { return lastSuccessAt; }

    public TopGainersPoller(TopGainersProperties settings, WebullProperties webull,
                           TopGainersGateway gateway, SymbolSelection selection, Clock clock) {
        this.settings = settings; this.webull = webull; this.gateway = gateway;
        this.selection = selection; this.clock = clock;
    }

    @Override public synchronized void start() {
        if (running || !settings.enabled() || !webull.streaming().enabled()) return;
        webull.validateCredentials();
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("webull-gainers").factory());
        executor.execute(this::runPoll);
    }

    private void runPoll() {
        if (!running) return;
        long start = System.nanoTime();
        long interval = pollOnce();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        synchronized (this) {
            if (running) executor.schedule(this::runPoll, failures == 0 ? Math.max(1, interval - elapsed) : interval, TimeUnit.MILLISECONDS);
        }
    }

    long pollOnce() {
        var requestAt = clock.instant();
        var session = GainersSession.at(requestAt, ZoneId.of(settings.zone()));
        try {
            var ranking = gateway.fetch(session);
            selection.replace(ranking);
            failures = 0;
            lastRankingRequestAt = requestAt;
            lastSuccessAt = clock.instant();
            log.debug("Webull gainers refreshed session={} returned={} eligible={}", session, ranking.size(), selection.symbols().size());
            return settings.pollIntervalMs();
        } catch (RuntimeException error) {
            failures = Math.min(failures + 1, 5);
            boolean throttled = "RATE_LIMITED".equals(WebullFailures.category(error));
            long delay = Math.min(300_000L, (throttled ? 60_000L : settings.pollIntervalMs()) * (1L << (failures - 1)));
            log.warn("Webull gainers refresh failed session={} category={} retryAfterMs={}", session, WebullFailures.category(error), delay);
            // Delay is measured after this failure, not from the request start.
            return delay;
        }
    }

    @Override public synchronized void stop() {
        running = false;
        if (executor != null) executor.shutdownNow();
    }
    @Override public boolean isRunning() { return running; }
}
