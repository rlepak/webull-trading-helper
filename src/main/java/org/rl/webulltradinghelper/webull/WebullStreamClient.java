package org.rl.webulltradinghelper.webull;

import com.webull.openapi.data.quotes.subscribe.IDataStreamingClient;
import com.webull.openapi.data.quotes.subscribe.message.MarketData;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class WebullStreamClient implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(WebullStreamClient.class);
    private final WebullProperties properties;
    private final WebullClientFactory factory;
    private final MarketDataLogger events;
    private final SymbolSelection selection;
    private volatile boolean running;
    private volatile boolean connected;
    private volatile long connectionGeneration;
    public long connectionGeneration() { return connectionGeneration; }
    public boolean isConnected() { return connected; }
    private volatile Thread worker;

    public WebullStreamClient(WebullProperties properties, WebullClientFactory factory, MarketDataLogger events, SymbolSelection selection) {
        this.properties = properties;
        this.factory = factory;
        this.events = events;
        this.selection = selection;
    }

    @Override
    public synchronized void start() {
        if (running || !properties.streaming().enabled()) return;
        properties.validateCredentials();
        running = true;
        worker = Thread.ofPlatform().daemon().name("webull-market-stream").start(this::run);
    }

    private void run() {
        long retrySeconds = 2;
        try {
            while (running) {
                long started = System.nanoTime();
                runSession();
                if (!running || Thread.currentThread().isInterrupted()) break;
                if (System.nanoTime() - started > TimeUnit.MINUTES.toNanos(1)) retrySeconds = 2;
                log.warn("Webull stream reconnect scheduled delaySeconds={}", retrySeconds);
                TimeUnit.SECONDS.sleep(retrySeconds);
                retrySeconds = Math.min(60, retrySeconds * 2);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
        }
    }

    private void runSession() {
        CountDownLatch disconnected = new CountDownLatch(1);
        IDataStreamingClient client = null;
        Disposable messages = null;
        try {
            log.debug("Connecting Webull market stream");
            var session = factory.create(properties, () -> { connected = false; disconnected.countDown(); });
            client = session.client();
            if (!running) return;
            messages = client.subscribeRx().subscribe(message -> {
                if (message instanceof MarketData marketData && selection.accept(marketData)) events.receive(marketData);
            }, error -> {
                log.warn("Webull inbound stream failed category={}", WebullFailures.category(error));
                connected = false;
                disconnected.countDown();
            });
            var ack = client.connectBlocking();
            if (!ack.isSuccess()) {
                log.warn("Webull MQTT connection rejected code={}", ack.getCode());
                return;
            }
            connectionGeneration++;
            connected = disconnected.getCount() != 0;
            log.debug("Webull MQTT connected; requesting data subscriptions");
            var reconciler = new SubscriptionReconciler(session, properties.streaming().types());
            while (running && disconnected.getCount() != 0) {
                reconciler.reconcile(selection.symbols(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
                disconnected.await(250, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            log.warn("Webull connection failed category={}", WebullFailures.category(error));
        } finally {
            connected = false;
            if (messages != null) messages.dispose();
            // Clear interruption while the SDK performs bounded disconnect/cleanup.
            boolean interrupted = Thread.interrupted();
            if (client != null) {
                try { client.disconnectBlocking(); }
                catch (RuntimeException error) { log.debug("Webull disconnect finished with a transport error"); }
                try { client.close(); }
                catch (RuntimeException error) { log.warn("Webull client cleanup failed"); }
            }
            if (interrupted) Thread.currentThread().interrupt();
            log.debug("Webull market stream session closed");
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        Thread current = worker;
        if (current != null) {
            current.interrupt();
            try { current.join(properties.streaming().timeoutMs() * 2L + 1000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            if (current.isAlive()) log.warn("Webull worker still finishing bounded SDK cleanup");
        }
    }

    @Override
    public boolean isRunning() { return running; }
}
