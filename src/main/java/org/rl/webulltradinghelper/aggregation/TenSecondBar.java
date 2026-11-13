package org.rl.webulltradinghelper.aggregation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A candle of observed trades; completed does not imply complete exchange coverage. */
public record TenSecondBar(
        String symbol, OffsetDateTime start, OffsetDateTime end, TimeBasis timeBasis,
        boolean completed, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
        BigDecimal volume, BigDecimal vwap, boolean volumeComplete, long tradeCount) {
    public enum TimeBasis { RECEIVE_TIME }
}
