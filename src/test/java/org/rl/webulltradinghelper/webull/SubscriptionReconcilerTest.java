package org.rl.webulltradinghelper.webull;

import com.webull.openapi.data.quotes.api.subsribe.IMarketStreamingClient;
import com.webull.openapi.data.quotes.subscribe.IDataStreamingClient;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionReconcilerTest {
    private final IMarketStreamingClient api = mock(IMarketStreamingClient.class);
    private final WebullStreamSession session = new WebullStreamSession(mock(IDataStreamingClient.class), api, "test-session");

    @Test void reconcilesDeltasWithoutResubscribingUnchangedSymbols() {
        var r = new SubscriptionReconciler(session, List.of("QUOTE"));
        r.reconcile(Set.of("A", "B"), 0);
        verify(api).subscribe("test-session", Set.of("A", "B"), "US_STOCK", Set.of("QUOTE"), "1", false);
        r.reconcile(Set.of("B", "A"), 2000);
        verifyNoMoreInteractions(api);
        r.reconcile(Set.of("B", "C"), 3000);
        verify(api).unsubscribe("test-session", Set.of("A"), "US_STOCK", Set.of("QUOTE"), false);
        r.reconcile(Set.of("B", "C"), 5000);
        verify(api).subscribe("test-session", Set.of("C"), "US_STOCK", Set.of("QUOTE"), "1", false);
        r.reconcile(Set.of(), 7000);
        verify(api).unsubscribe("test-session", Set.of("B", "C"), "US_STOCK", Set.of("QUOTE"), false);
    }

    @Test void failedRemovalRemainsPendingAndDoesNotBlockOtherTypes() {
        var r = new SubscriptionReconciler(session, List.of("QUOTE", "TICK"));
        r.reconcile(Set.of("A"), 0); r.reconcile(Set.of("A"), 2000);
        doThrow(new IllegalStateException("private-response")).doNothing().when(api)
                .unsubscribe("test-session", Set.of("A"), "US_STOCK", Set.of("QUOTE"), false);
        r.reconcile(Set.of(), 4000);
        r.reconcile(Set.of(), 6000);
        verify(api).unsubscribe("test-session", Set.of("A"), "US_STOCK", Set.of("TICK"), false);
        r.reconcile(Set.of(), 10000);
        verify(api, times(1)).unsubscribe("test-session", Set.of("A"), "US_STOCK", Set.of("QUOTE"), false);
        r.reconcile(Set.of(), 36000);
        verify(api, times(2)).unsubscribe("test-session", Set.of("A"), "US_STOCK", Set.of("QUOTE"), false);
    }

    @Test void batchesLargeSelectionsWithoutTruncatingThem() {
        var r = new SubscriptionReconciler(session, List.of("TICK"));
        Set<String> symbols = IntStream.range(0, 121).mapToObj(i -> "S" + i).collect(Collectors.toSet());
        r.reconcile(symbols, 0); r.reconcile(symbols, 2000); r.reconcile(symbols, 4000);
        var captor = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(api, times(3)).subscribe(eq("test-session"), captor.capture(), eq("US_STOCK"), eq(Set.of("TICK")), eq("1"), eq(false));
        assertEquals(List.of(50, 50, 21), captor.getAllValues().stream().map(Set::size).toList());
        assertEquals(symbols, captor.getAllValues().stream().flatMap(Set::stream).collect(Collectors.toSet()));
    }
}
