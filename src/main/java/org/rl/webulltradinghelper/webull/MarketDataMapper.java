package org.rl.webulltradinghelper.webull;

import com.webull.openapi.data.quotes.domain.*;
import com.webull.openapi.data.quotes.subscribe.message.MarketData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent.*;
import org.springframework.stereotype.Component;

@Component
public class MarketDataMapper {
    public MarketDataEvent map(MarketData message, Instant receivedAt) {
        if (message == null || message.getData() == null) {
            throw new IllegalArgumentException("Missing market payload");
        }
        QuotesBasic data = message.getData();
        if (data.getSymbol() == null || !data.getSymbol().matches("[A-Za-z0-9][A-Za-z0-9./-]{0,19}")) {
            throw new IllegalArgumentException("Missing or invalid symbol");
        }
        Payload payload;
        String type;
        switch (data) {
            case Quote quote -> {
                type = "QUOTE";
                payload = new QuoteData(quote.getQuoteTime(), quote.getTradingSession(),
                        levels(quote.getBids()), levels(quote.getAsks()));
            }
            case Snapshot snapshot -> {
                type = "SNAPSHOT";
                payload = snapshot(snapshot);
            }
            case Tick tick -> {
                type = "TICK";
                payload = new TickData(tick.getResult() == null ? List.of() : tick.getResult().stream()
                        .map(t -> new Trade(t.getTime(), decimal(t.getPrice()), decimal(t.getVolume()),
                                t.getSide(), t.getTradingSession())).toList());
            }
            default -> throw new IllegalArgumentException("Unsupported market payload");
        }
        return new MarketDataEvent("WEBULL", type, data.getSymbol(), data.getInstrumentId(),
                message.getTimestamp(), receivedAt, payload);
    }

    private List<PriceLevel> levels(List<AskBid> levels) {
        return levels == null ? List.of() : levels.stream()
                .map(l -> new PriceLevel(decimal(l.getPrice()), decimal(l.getSize()))).toList();
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private SnapshotData snapshot(Snapshot snapshot) {
        return new SnapshotData(
                snapshot.getTradeTime(),
                snapshot.getLastTradeTime(),
                decimal(snapshot.getPrice()),
                decimal(snapshot.getOpen()),
                decimal(snapshot.getHigh()),
                decimal(snapshot.getLow()),
                decimal(snapshot.getPreClose()),
                decimal(snapshot.getClose()),
                decimal(snapshot.getVolume()),
                decimal(snapshot.getChange()),
                decimal(snapshot.getChangeRatio()),
                snapshot.getOvnLastTradeTime(),
                decimal(snapshot.getOvnPrice()),
                decimal(snapshot.getOvnHigh()),
                decimal(snapshot.getOvnLow()),
                decimal(snapshot.getOvnVolume()),
                decimal(snapshot.getOvnChange()),
                decimal(snapshot.getOvnChangeRatio()),
                snapshot.getExtendHourLastTradeTime(),
                decimal(snapshot.getExtendHourLastPrice()),
                decimal(snapshot.getExtendHourHigh()),
                decimal(snapshot.getExtendHourLow()),
                decimal(snapshot.getExtendHourVolume()),
                decimal(snapshot.getExtendHourChange()),
                decimal(snapshot.getExtendHourChangeRatio()),
                snapshot.getTradingSession(),
                decimal(snapshot.getOpenInterest()),
                snapshot.getQuoteTime(),
                decimal(snapshot.getBid()),
                decimal(snapshot.getAsk()),
                decimal(snapshot.getAskSize()),
                decimal(snapshot.getBidSize()),
                decimal(snapshot.getOvnBid()),
                decimal(snapshot.getOvnAsk()),
                decimal(snapshot.getOvnBidSize()),
                decimal(snapshot.getOvnAskSize()),
                snapshot.getSettleDate(),
                decimal(snapshot.getSettlePrice()),
                decimal(snapshot.getStrikePrice()),
                decimal(snapshot.getGamma()),
                decimal(snapshot.getDelta()),
                decimal(snapshot.getRho()),
                decimal(snapshot.getTheta()),
                decimal(snapshot.getVega()),
                decimal(snapshot.getImpVol()),
                decimal(snapshot.getDealAmount()),
                decimal(snapshot.getTurnover()),
                decimal(snapshot.getEps()),
                decimal(snapshot.getEpsTtm()),
                decimal(snapshot.getLotSize()),
                decimal(snapshot.getBps())        );
    }
}
