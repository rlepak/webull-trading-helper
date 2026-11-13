package org.rl.webulltradinghelper.webull;

import com.webull.openapi.data.DataClient;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TopGainersGateway {
    private final WebullProperties properties;
    private DataClient client;

    public TopGainersGateway(WebullProperties properties) { this.properties = properties; }

    public List<GainerStock> fetch(GainersSession session) {
        if (client == null) client = new DataClient(WebullClientFactory.httpConfig(properties));
        var response = client.getGainersLosers(session.name(), "US_STOCK", "CHANGE_RATIO", 1, 200, "DESC");
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Missing gainers response data");
        }
        return response.getData().stream().filter(java.util.Objects::nonNull)
                .map(row -> new GainerStock(row.getSymbol(), decimal(row.getPrice()), decimal(row.getChangeRatio())))
                .toList();
    }

    private static BigDecimal decimal(String value) {
        try { return value == null ? null : new BigDecimal(value); }
        catch (NumberFormatException invalid) { return null; }
    }
}
