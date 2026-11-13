package org.rl.webulltradinghelper.webull.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Application-owned immutable data, independent of the Webull transport SDK. */
public record MarketDataEvent(String source, String type, String symbol, String instrumentId,
                              String providerTimestamp, Instant receivedAt, Payload payload) {
    public sealed interface Payload permits QuoteData, SnapshotData, TickData {}

    public record PriceLevel(BigDecimal price, BigDecimal size) {}

    public record QuoteData(Long quoteTime, String tradingSession,
                            List<PriceLevel> bids, List<PriceLevel> asks) implements Payload {
        public QuoteData {
            bids = List.copyOf(bids);
            asks = List.copyOf(asks);
        }
    }

    public record Trade(String time, BigDecimal price, BigDecimal volume,
                        String side, String tradingSession) {}

    public record TickData(List<Trade> trades) implements Payload {
        public TickData { trades = List.copyOf(trades); }
    }
    public record SnapshotData(
            String tradeTime,
            Long lastTradeTime,
            BigDecimal price,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal preClose,
            BigDecimal close,
            BigDecimal volume,
            BigDecimal change,
            BigDecimal changeRatio,
            Long ovnLastTradeTime,
            BigDecimal ovnPrice,
            BigDecimal ovnHigh,
            BigDecimal ovnLow,
            BigDecimal ovnVolume,
            BigDecimal ovnChange,
            BigDecimal ovnChangeRatio,
            Long extendHourLastTradeTime,
            BigDecimal extendHourLastPrice,
            BigDecimal extendHourHigh,
            BigDecimal extendHourLow,
            BigDecimal extendHourVolume,
            BigDecimal extendHourChange,
            BigDecimal extendHourChangeRatio,
            String tradingSession,
            BigDecimal openInterest,
            Long quoteTime,
            BigDecimal bid,
            BigDecimal ask,
            BigDecimal askSize,
            BigDecimal bidSize,
            BigDecimal ovnBid,
            BigDecimal ovnAsk,
            BigDecimal ovnBidSize,
            BigDecimal ovnAskSize,
            String settleDate,
            BigDecimal settlePrice,
            BigDecimal strikePrice,
            BigDecimal gamma,
            BigDecimal delta,
            BigDecimal rho,
            BigDecimal theta,
            BigDecimal vega,
            BigDecimal impVol,
            BigDecimal dealAmount,
            BigDecimal turnover,
            BigDecimal eps,
            BigDecimal epsTtm,
            BigDecimal lotSize,
            BigDecimal bps
    ) implements Payload {}
}
