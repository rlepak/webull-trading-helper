package org.rl.webulltradinghelper.webull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owned by the socket worker. Failed operations never change confirmed state. */
final class SubscriptionReconciler {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionReconciler.class);
    private final WebullStreamSession session;
    private final Map<String, Set<String>> active = new LinkedHashMap<>();
    private final Map<String, Long> retryAfter = new LinkedHashMap<>();
    private long nextOperation;

    SubscriptionReconciler(WebullStreamSession session, List<String> types) {
        this.session = session;
        types.forEach(type -> active.put(type, new LinkedHashSet<>()));
    }

    void reconcile(Set<String> desired, long nowMs) {
        if (nowMs < nextOperation) return;
        // One paced call per pass, with removals ahead of additions across all event types.
        for (boolean removing : List.of(true, false)) {
            for (var entry : active.entrySet()) {
                String type = entry.getKey();
                if (nowMs < retryAfter.getOrDefault(type, 0L)) continue;
                Set<String> delta = new LinkedHashSet<>(removing ? entry.getValue() : desired);
                delta.removeAll(removing ? desired : entry.getValue());
                if (delta.isEmpty()) continue;
                Set<String> batch = new LinkedHashSet<>(delta.stream().limit(50).toList());
                long operationStarted = System.nanoTime();
                try {
                    if (removing) {
                        session.remove(batch, type);
                        entry.getValue().removeAll(batch);
                    } else {
                        session.add(batch, type);
                        entry.getValue().addAll(batch);
                    }
                    retryAfter.remove(type);
                    log.debug("Webull subscription updated action={} type={} symbols={}", removing ? "REMOVE" : "ADD", type, batch);
                } catch (RuntimeException error) {
                    String category = WebullFailures.category(error);
                    retryAfter.put(type, nowMs + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - operationStarted)
                            + ("RATE_LIMITED".equals(category) ? 60_000 : 30_000));
                    log.warn("Webull subscription update failed action={} type={} category={} httpStatus={}", removing ? "REMOVE" : "ADD", type, category, WebullFailures.httpStatus(error));
                }
                nextOperation = nowMs + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - operationStarted) + 1_100;
                return;
            }
        }
    }
}
