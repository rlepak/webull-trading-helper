package org.rl.webulltradinghelper.dashboard;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.rl.webulltradinghelper.aggregation.TenSecondBar;
import static org.assertj.core.api.Assertions.assertThat;

class VolumeSurgeTest {
    private final DashboardProperties settings = new DashboardProperties(new BigDecimal("3"), 3, 10, 15);
    private final Instant now = Instant.parse("2026-11-18T13:00:35Z");
    private final Instant start = now.minusSeconds(35);
    private TenSecondBar bar(int offset, String volume, boolean complete) {
        var time = start.plusSeconds(offset).atOffset(ZoneOffset.UTC);
        return new TenSecondBar("TEST", time, time.plusSeconds(10), TenSecondBar.TimeBasis.RECEIVE_TIME,
                complete, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                volume == null ? null : new BigDecimal(volume), null, volume != null, 1);
    }
    @Test void triggersExactlyAtThreeTimesMeanWithoutWaitingForClose() {
        var prior = List.of(bar(0,"100",true), bar(10,"200",true), bar(20,"300",true));
        assertThat(VolumeSurge.evaluate(prior, bar(30,"599",false), now,start,settings).triggered()).isFalse();
        var result = VolumeSurge.evaluate(prior,bar(30,"600",false),now,start,settings);
        assertThat(result.triggered()).isTrue();
        assertThat(result.baseline()).isEqualByComparingTo("200");
        assertThat(result.multiplier()).isEqualByComparingTo("3");
    }
    @Test void excludesInitialPartialBarAndMissingOrUnknownWindows() {
        var prior = List.of(bar(0,"100",true),bar(10,"200",true),bar(20,"300",true));
        assertThat(VolumeSurge.evaluate(prior,bar(30,"900",false),now,start.plusSeconds(10),settings).status()).isEqualTo("Warming up");
        assertThat(VolumeSurge.evaluate(prior.subList(1,3),bar(30,"900",false),now,start,settings).triggered()).isFalse();
        assertThat(VolumeSurge.evaluate(List.of(bar(0,null,true),bar(10,"200",true),bar(20,"300",true)),bar(30,"900",false),now,start,settings).status()).isEqualTo("Volume unavailable");
        assertThat(VolumeSurge.evaluate(prior,bar(30,null,false),now,start,settings).triggered()).isFalse();
    }
    @Test void zeroBaselineAndOldFormingBarDoNotTrigger() {
        var zero = List.of(bar(0,"0",true),bar(10,"0",true),bar(20,"0",true));
        assertThat(VolumeSurge.evaluate(zero,bar(30,"900",false),now,start,settings).status()).isEqualTo("Zero baseline");
        var prior = List.of(bar(0,"100",true),bar(10,"100",true),bar(20,"100",true));
        assertThat(VolumeSurge.evaluate(prior,bar(20,"900",false),now,start,settings).triggered()).isFalse();
    }
}
