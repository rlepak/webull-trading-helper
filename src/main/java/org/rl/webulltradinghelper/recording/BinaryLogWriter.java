package org.rl.webulltradinghelper.recording;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.*;
import org.rl.webulltradinghelper.webull.GainersSession;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class BinaryLogWriter implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(BinaryLogWriter.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd-yy");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH");
    private final BinaryLogProperties settings;
    private final Clock clock;
    private final ObjectMapper json;
    private final BlockingQueue<Pending> queue;
    private volatile boolean accepting;
    private volatile boolean running;
    private volatile boolean failed;
    private volatile long written;
    private Thread worker;
    private FileChannel lockChannel;
    private FileLock directoryLock;
    private String recordingId;
    private long sequence;
    private BinaryLogFile file;
    private Path currentPath;

    public BinaryLogWriter(BinaryLogProperties settings, Clock clock, ObjectMapper json) {
        this.settings = settings;
        this.clock = clock;
        this.json = json;
        queue = new ArrayBlockingQueue<>(settings.queueCapacity());
    }

    @Override public synchronized void start() {
        if (!settings.enabled() || running) return;
        if (failed) throw new IllegalStateException("Binary recorder requires a fresh instance after failure");
        try {
            Files.createDirectories(settings.directory());
            lockChannel = FileChannel.open(settings.directory().resolve(".writer.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            directoryLock = lockChannel.tryLock();
            if (directoryLock == null) throw new IOException("Recording directory is already in use");
            for (String session : new String[]{"premarket", "market", "aftermarket"}) {
                Files.createDirectories(settings.directory().resolve(session));
            }
            recordingId = UUID.randomUUID().toString();
            sequence = 0;
            accepting = running = true;
            worker = Thread.ofPlatform().name("binary-market-writer").daemon().start(this::writeLoop);
        } catch (IOException | RuntimeException error) {
            closeLock();
            failed = true;
            throw new IllegalStateException("Cannot start binary recorder; check directory access and writer ownership");
        }
    }

    /** True means queued, not yet durable. No filesystem calls run on the feed callback. */
    public synchronized boolean record(MarketDataEvent event) {
        if (!settings.enabled()) return true;
        if (!accepting || failed) return false;
        try {
            var envelope = json.createObjectNode();
            envelope.put("schemaVersion", 1);
            envelope.put("recordingId", recordingId);
            envelope.put("sequenceNumber", ++sequence);
            String received = event.receivedAt().atZone(clock.getZone()).toOffsetDateTime().toString();
            envelope.put("receivedAt", received);
            var body = json.valueToTree(event);
            ((tools.jackson.databind.node.ObjectNode) body).put("receivedAt", received);
            envelope.set("event", body);
            byte[] bytes = json.writeValueAsBytes(envelope);
            if (bytes.length > BinaryLogFile.MAX_RECORD_BYTES) {
                fail("RECORD_TOO_LARGE");
                return false;
            }
            if (!queue.offer(new Pending(event.receivedAt(), bytes))) {
                fail("QUEUE_FULL");
                return false;
            }
            return true;
        } catch (RuntimeException error) {
            fail("ENCODING");
            return false;
        }
    }

    private void writeLoop() {
        long nextForce = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(settings.flushIntervalMs());
        try {
            while (accepting || !queue.isEmpty()) {
                long remaining = Math.max(1, TimeUnit.NANOSECONDS.toMillis(nextForce - System.nanoTime()));
                Pending item = queue.poll(Math.min(remaining, 100), TimeUnit.MILLISECONDS);
                if (item != null) {
                    Path target = targetFile(item.receivedAt());
                    if (!target.equals(currentPath)) {
                        closeFile();
                        file = openFile(target);
                        currentPath = target;
                        log.debug("Writing binary market data file={}", target);
                    }
                    file.write(item.bytes());
                    written++;
                }
                if (System.nanoTime() >= nextForce) {
                    if (file != null) file.force();
                    nextForce = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(settings.flushIntervalMs());
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            fail("SHUTDOWN_INTERRUPTED");
        } catch (IOException | RuntimeException error) {
            fail("DISK_WRITE");
        } finally {
            try { closeFile(); }
            catch (IOException | RuntimeException error) { fail("DISK_FLUSH"); }
            if (!queue.isEmpty()) log.error("Binary recording stopped with unrecorded queued events count={}", queue.size());
            queue.clear();
            closeLock();
            running = false;
        }
    }

    Path targetFile(Instant receivedAt) {
        String session = switch (GainersSession.at(receivedAt, clock.getZone())) {
            case PRE_MARKET -> "premarket";
            case DAY_1 -> "market";
            case AFTER_MARKET -> "aftermarket";
        };
        var local = receivedAt.atZone(clock.getZone());
        return settings.directory().resolve(session).resolve(DATE.format(local))
                .resolve("webull-" + HOUR.format(local) + ".bin");
    }

    BinaryLogFile openFile(Path path) throws IOException { return new BinaryLogFile(path, json); }

    private synchronized void fail(String category) {
        failed = true;
        accepting = false;
        log.error("Binary recording failed category={} queued={} written={}; recording is unhealthy",
                category, queue.size(), written);
    }

    private void closeFile() throws IOException {
        if (file == null) return;
        try { file.close(); }
        finally { file = null; currentPath = null; }
    }

    private void closeLock() {
        try { if (directoryLock != null) directoryLock.release(); }
        catch (IOException error) { log.error("Binary recording directory lock release failed"); }
        try { if (lockChannel != null) lockChannel.close(); }
        catch (IOException error) { log.error("Binary recording lock channel close failed"); }
        directoryLock = null;
        lockChannel = null;
    }

    @Override public void stop() {
        Thread thread;
        synchronized (this) {
            accepting = false;
            thread = worker;
        }
        if (thread != null) {
            try {
                thread.join(10_000);
                if (thread.isAlive()) {
                    fail("SHUTDOWN_TIMEOUT");
                    thread.interrupt();
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                fail("SHUTDOWN_INTERRUPTED");
            }
        }
    }

    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
    public int queuedRecords() { return queue.size(); }
    public boolean isHealthy() { return !failed; }
    public long writtenRecords() { return written; }
    private record Pending(Instant receivedAt, byte[] bytes) {}
}
