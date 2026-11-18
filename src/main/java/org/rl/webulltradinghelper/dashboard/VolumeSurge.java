package org.rl.webulltradinghelper.dashboard;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import org.rl.webulltradinghelper.aggregation.TenSecondBar;

/** Pure calculation over consecutive completed receive-time bars. Never fills gaps. */
final class VolumeSurge {
    record Result(BigDecimal baseline, BigDecimal multiplier, boolean triggered, String status) {}

    static Result evaluate(List<TenSecondBar> history, TenSecondBar current, Instant now,
                           Instant eligibleFrom, DashboardProperties settings) {
        long start = Math.floorDiv(now.getEpochSecond(), 10) * 10;
        BigDecimal sum = BigDecimal.ZERO;
        for (int offset = 1; offset <= settings.baselineBars(); offset++) {
            Instant expected = Instant.ofEpochSecond(start - offset * 10L);
            if (expected.isBefore(eligibleFrom)) return unavailable("Warming up");
            var bar = history.stream().filter(b -> b.completed() && b.start().toInstant().equals(expected))
                    .findFirst().orElse(null);
            if (bar == null) return unavailable("Waiting for consecutive bars");
            if (!bar.volumeComplete() || bar.volume() == null) return unavailable("Volume unavailable");
            sum = sum.add(bar.volume());
        }
        if (sum.signum() == 0) return unavailable("Zero baseline");
        var baseline = sum.divide(BigDecimal.valueOf(settings.baselineBars()), MathContext.DECIMAL64);
        if (current == null || !current.start().toInstant().equals(Instant.ofEpochSecond(start)))
            return new Result(baseline, BigDecimal.ZERO, false, "Watching");
        if (!current.volumeComplete() || current.volume() == null) return unavailable("Volume unavailable");
        var ratio = current.volume().divide(baseline, MathContext.DECIMAL64);
        boolean triggered = current.volume().multiply(BigDecimal.valueOf(settings.baselineBars()))
                .compareTo(sum.multiply(settings.volumeMultiplier())) >= 0;
        return new Result(baseline, ratio, triggered, "Watching");
    }
    private static Result unavailable(String status) { return new Result(null, null, false, status); }
}
