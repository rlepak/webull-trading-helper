package org.rl.webulltradinghelper.webull;

import com.webull.openapi.core.http.HttpApiConfig;
import com.webull.openapi.core.http.RuntimeOptions;
import com.webull.openapi.core.retry.RetryPolicy;
import com.webull.openapi.data.quotes.api.subsribe.MarketStreamingClient;
import com.webull.openapi.data.quotes.subscribe.IDataStreamingClient;
import com.webull.openapi.data.quotes.subscribe.lifecycle.QuotesSubsConnectedContext;
import com.webull.openapi.data.quotes.subscribe.lifecycle.QuotesSubsFailedContext;
import com.webull.openapi.data.quotes.subscribe.lifecycle.QuotesSubsSessionHandler;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class WebullClientFactory {
    public WebullStreamSession create(WebullProperties properties, Runnable disconnected) {
        var settings = properties.streaming();
        var api = new MarketStreamingClient(httpConfig(properties));
        String sessionId = UUID.randomUUID().toString();
        var client = IDataStreamingClient.builder()
                .appKey(properties.appKey()).appSecret(properties.appSecret())
                .sessionId(sessionId).apiClient(api)
                .mqtt_host(settings.host()).mqtt_port(settings.port()).enableTls(true)
                .connectTimeout(settings.timeoutMs()).readTimeout(settings.timeoutMs())
                .reconnectBy(RetryPolicy.never())
                .addHandler(new QuotesSubsSessionHandler() {
                    @Override public void onConnected(QuotesSubsConnectedContext context) { }
                    @Override public void onDisconnected(QuotesSubsFailedContext context) { disconnected.run(); }
                }).build();
        return new WebullStreamSession(client, api, sessionId);
    }

    static HttpApiConfig httpConfig(WebullProperties properties) {
        var settings = properties.streaming();
        return HttpApiConfig.builder()
                .appKey(properties.appKey()).appSecret(properties.appSecret())
                .regionId(properties.regionId()).endpoint(properties.endpoint())
                .autoRetry(false).maxRetryNum(0).tokenDir(".webull")
                .runtimeOptions(new RuntimeOptions()
                        .connectTimeout(settings.timeoutMs(), TimeUnit.MILLISECONDS)
                        .readTimeout(settings.timeoutMs(), TimeUnit.MILLISECONDS))
                .build();
    }
}
