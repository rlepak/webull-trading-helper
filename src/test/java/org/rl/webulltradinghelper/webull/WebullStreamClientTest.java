package org.rl.webulltradinghelper.webull;

import com.webull.openapi.data.quotes.subscribe.IDataStreamingClient;
import com.webull.openapi.data.quotes.api.subsribe.IMarketStreamingClient;
import com.webull.openapi.data.quotes.subscribe.message.ConnAck;
import io.reactivex.Flowable;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebullStreamClientTest {
    @Test void disabledStreamNeverCreatesClient() {
        var factory = mock(WebullClientFactory.class);
        var stream = new WebullStreamClient(WebullPropertiesTest.properties(false), factory, mock(MarketDataLogger.class), new SymbolSelection(TopGainersTest.settings(false), WebullPropertiesTest.properties(true)));
        stream.start(); stream.stop();
        verifyNoInteractions(factory);
        assertFalse(stream.isRunning());
    }

    @Test void failedSubscriptionDoesNotBlockOthersAndStopClosesClient() {
        var factory = mock(WebullClientFactory.class);
        var client = mock(IDataStreamingClient.class);
        var api = mock(IMarketStreamingClient.class);
        when(factory.create(any(), any())).thenReturn(new WebullStreamSession(client, api, "test-session"));
        when(client.subscribeRx()).thenReturn(Flowable.never());
        when(client.connectBlocking()).thenReturn(new ConnAck(0));
        doThrow(new IllegalStateException("private-response"))
                .when(api).subscribe(anyString(), anySet(), anyString(), eq(Set.of("QUOTE")), anyString(), anyBoolean());
        var stream = new WebullStreamClient(WebullPropertiesTest.properties(true), factory, mock(MarketDataLogger.class), new SymbolSelection(TopGainersTest.settings(false), WebullPropertiesTest.properties(true)));
        try {
            stream.start();
            verify(api, timeout(5000)).subscribe("test-session", Set.of("AAPL"), "US_STOCK", Set.of("TICK"), "1", false);
        } finally { stream.stop(); }
        verify(client).close();
        verify(client).disconnectBlocking();
        assertFalse(stream.isRunning());
    }

    @Test void disconnectRecreatesSessionWithLatestSelection() {
        var factory = mock(WebullClientFactory.class);
        var client = mock(IDataStreamingClient.class);
        var api = mock(IMarketStreamingClient.class);
        AtomicReference<Runnable> disconnect = new AtomicReference<>();
        when(factory.create(any(), any())).thenAnswer(call -> { disconnect.set(call.getArgument(1)); return new WebullStreamSession(client, api, "test-session"); });
        when(client.subscribeRx()).thenReturn(Flowable.never());
        when(client.connectBlocking()).thenReturn(new ConnAck(0));
        var selection = new SymbolSelection(TopGainersTest.settings(true), WebullPropertiesTest.properties(true));
        selection.replace(java.util.List.of(TopGainersTest.stock("OLD", "1")));
        var stream = new WebullStreamClient(WebullPropertiesTest.properties(true), factory, mock(MarketDataLogger.class), selection);
        try {
            stream.start();
            verify(api, timeout(5000)).subscribe(anyString(), anySet(), anyString(), eq(Set.of("TICK")), anyString(), anyBoolean());
            selection.replace(java.util.List.of(TopGainersTest.stock("NEW", "2")));
            disconnect.get().run();
            verify(api, timeout(7000)).subscribe(eq("test-session"), eq(Set.of("NEW")), eq("US_STOCK"), eq(Set.of("TICK")), eq("1"), eq(false));
        } finally { stream.stop(); }
        verify(client, times(2)).close();
    }
}
