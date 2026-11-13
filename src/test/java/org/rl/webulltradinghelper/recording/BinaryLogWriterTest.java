package org.rl.webulltradinghelper.recording;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent;
import org.rl.webulltradinghelper.webull.model.MarketDataEvent.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class BinaryLogWriterTest {
    @TempDir Path directory;
    final Clock clock = Clock.system(ZoneId.of("America/Chicago"));
    final JsonMapper json = JsonMapper.builder().build();
    final Instant time = Instant.parse("2026-11-12T19:00:00.123456789Z");
    BinaryLogProperties settings(boolean enabled) { return new BinaryLogProperties(enabled, directory, 100, 20); }
    BinaryLogWriter writer() { return new BinaryLogWriter(settings(true), clock, json); }
    MarketDataEvent event(Instant instant) {
        return new MarketDataEvent("WEBULL", "QUOTE", "TEST", "id", "unmodified",
                instant, new QuoteData(null, "RTH",
                List.of(new PriceLevel(new BigDecimal("1.234567890123456789"), null)), List.of()));
    }
    List<JsonNode> records(Path path) throws IOException {
        var result = new ArrayList<JsonNode>();
        try (var input = new DataInputStream(Files.newInputStream(path))) {
            while (input.available() != 0) {
                int size = input.readInt();
                byte[] bytes = input.readNBytes(size);
                assertEquals(size, bytes.length);
                result.add(json.readTree(bytes));
            }
        }
        return result;
    }

    @Test void recordsContentInOrderDrainsAndAppendsOnRestart() throws Exception {
        var first = writer(); first.start();
        try {
            assertTrue(first.record(event(time)));
            assertTrue(first.record(new MarketDataEvent("WEBULL", "TICK", "TEST", "id", "provider",
                    time.plusNanos(1), new TickData(List.of(new Trade("13:00:00", new BigDecimal("1.2"), null, "N", "RTH"))))));
        } finally { first.stop(); }
        assertFalse(first.isRunning());
        Path path = first.targetFile(time);
        var before = records(path);
        assertEquals(2, before.size());
        assertEquals(1, before.getFirst().path("sequenceNumber").asLong());
        assertEquals(2, before.getLast().path("sequenceNumber").asLong());
        assertEquals("2026-11-12T13:00:00.123456789-06:00", before.getFirst().path("receivedAt").asText());
        String raw = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(raw.contains("1.234567890123456789"));
        assertTrue(before.getFirst().path("event").path("payload").path("bids").get(0).path("size").isNull());
        assertEquals("unmodified", before.getFirst().path("event").path("providerTimestamp").asText());
        var next = writer(); next.start();
        try { assertTrue(next.record(event(time.plusSeconds(1)))); } finally { next.stop(); }
        var after = records(path);
        assertEquals(3, after.size());
        assertEquals(before, after.subList(0, 2));
        assertNotEquals(after.getFirst().path("recordingId"), after.getLast().path("recordingId"));
        assertEquals(1, after.getLast().path("sequenceNumber").asLong());
    }

    @Test void routesSessionHourDateAndDstByChicagoReceiveTime() throws Exception {
        var writer = writer();
        String[][] cases = {
                {"2026-11-12T02:59:59-06:00", "aftermarket/11-12-26/webull-02.bin"},
                {"2026-11-12T03:00:00-06:00", "premarket/11-12-26/webull-03.bin"},
                {"2026-11-12T08:29:59-06:00", "premarket/11-12-26/webull-08.bin"},
                {"2026-11-12T08:30:00-06:00", "market/11-12-26/webull-08.bin"},
                {"2026-11-12T09:00:00-06:00", "market/11-12-26/webull-09.bin"},
                {"2026-11-12T14:59:59-06:00", "market/11-12-26/webull-14.bin"},
                {"2026-11-12T15:00:00-06:00", "aftermarket/11-12-26/webull-15.bin"},
                {"2026-11-13T00:00:00-06:00", "aftermarket/11-13-26/webull-00.bin"},
                {"2026-07-12T08:30:00-05:00", "market/07-12-26/webull-08.bin"},
                {"2026-11-01T01:30:00-05:00", "aftermarket/11-01-26/webull-01.bin"},
                {"2026-11-01T01:30:00-06:00", "aftermarket/11-01-26/webull-01.bin"}
        };
        writer.start();
        try {
            for (String[] item : cases) {
                Instant instant = OffsetDateTime.parse(item[0]).toInstant();
                assertEquals(directory.resolve(item[1]), writer.targetFile(instant));
                assertTrue(writer.record(event(instant)));
            }
        } finally { writer.stop(); }
        for (String[] item : cases) assertTrue(Files.exists(directory.resolve(item[1])));
        var repeated = records(directory.resolve(cases[9][1]));
        assertEquals(2, repeated.size());
        assertTrue(repeated.getFirst().path("receivedAt").asText().endsWith("-05:00"));
        assertTrue(repeated.getLast().path("receivedAt").asText().endsWith("-06:00"));
    }

    @Test void flushesWhileRunningAndExcludesSecondWriter() throws Exception {
        var writer = writer(); writer.start();
        try {
            var second = writer();
            assertThrows(IllegalStateException.class, second::start);
            assertTrue(writer.record(event(time)));
            Path path = writer.targetFile(time);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while ((!Files.exists(path) || Files.size(path) == 0) && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(1, records(path).size());
        } finally { writer.stop(); }
    }

    @Test void repairsOnlyIncompleteTailAndRejectsCorruption() throws Exception {
        var writer = writer(); writer.start();
        try { writer.record(event(time)); } finally { writer.stop(); }
        Path path = writer.targetFile(time);
        byte[] complete = Files.readAllBytes(path);
        for (byte[] partial : List.of(new byte[]{0, 0}, new byte[]{0, 0, 0, 10, 123})) {
            Files.write(path, complete);
            Files.write(path, partial, StandardOpenOption.APPEND);
            try (var file = new BinaryLogFile(path, json)) {}
            assertArrayEquals(complete, Files.readAllBytes(path));
        }
        Files.write(path, new byte[]{-1, -1, -1, -1}, StandardOpenOption.APPEND);
        byte[] corrupt = Files.readAllBytes(path);
        assertThrows(IOException.class, () -> new BinaryLogFile(path, json));
        assertArrayEquals(corrupt, Files.readAllBytes(path));
        Files.write(path, new byte[]{0, 0, 0, 2, 123, 125});
        assertThrows(IOException.class, () -> new BinaryLogFile(path, json));
    }

    @Test void disabledCreatesNothingAndDiskFailureIsUnhealthy() throws Exception {
        Path absent = directory.resolve("disabled");
        var disabled = new BinaryLogWriter(new BinaryLogProperties(false, absent, 1, 20), clock, json);
        disabled.start(); disabled.record(event(time)); disabled.stop();
        assertFalse(Files.exists(absent));
        var writer = writer();
        Files.createDirectories(writer.targetFile(time)); // A directory cannot be opened as an hourly file.
        writer.start();
        assertTrue(writer.record(event(time)));
        writer.stop();
        assertFalse(writer.isHealthy());
        assertFalse(writer.record(event(time)));
    }

    @Test void delayedDiskWritePreservesReceiveInstantsAndSpacing() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Clock laterClock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), clock.getZone());
        var writer = new BinaryLogWriter(settings(true), laterClock, json) {
            @Override BinaryLogFile openFile(Path path) throws IOException {
                entered.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("Timeout"); }
                catch (InterruptedException e) { throw new IOException(e); }
                return super.openFile(path);
            }
        };
        writer.start();
        Instant second = time.plusNanos(12_345_678);
        try {
            assertTrue(writer.record(event(time)));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertTrue(writer.record(event(second)));
        } finally { release.countDown(); writer.stop(); }
        var saved = records(writer.targetFile(time));
        assertEquals(2, saved.size());
        Instant firstSaved = OffsetDateTime.parse(saved.getFirst().path("receivedAt").asText()).toInstant();
        Instant secondSaved = OffsetDateTime.parse(saved.getLast().path("receivedAt").asText()).toInstant();
        assertEquals(time, firstSaved);
        assertEquals(second, secondSaved);
        assertEquals(12_345_678, Duration.between(firstSaved, secondSaved).toNanos());
        for (var record : saved) assertEquals(record.path("receivedAt"), record.path("event").path("receivedAt"));
    }

    @Test void rejectsMissingInvalidOrConflictingReceiveTimeWithoutChangingFile() throws Exception {
        var writer = writer(); writer.start();
        try { assertTrue(writer.record(event(time))); } finally { writer.stop(); }
        Path path = writer.targetFile(time);
        var original = (tools.jackson.databind.node.ObjectNode) records(path).getFirst();
        for (String invalid : List.of("missing", "invalid", "conflicting")) {
            var record = original.deepCopy();
            var event = (tools.jackson.databind.node.ObjectNode) record.path("event");
            switch (invalid) {
                case "missing" -> event.remove("receivedAt");
                case "invalid" -> record.put("receivedAt", "13:00:00");
                case "conflicting" -> event.put("receivedAt", "2026-11-12T19:00:01Z");
            }
            byte[] bytes = json.writeValueAsBytes(record);
            try (var output = new DataOutputStream(Files.newOutputStream(path))) {
                output.writeInt(bytes.length); output.write(bytes);
            }
            byte[] before = Files.readAllBytes(path);
            assertThrows(IOException.class, () -> new BinaryLogFile(path, json), invalid);
            assertArrayEquals(before, Files.readAllBytes(path), invalid);
        }
    }

    @Test void overflowRejectsFurtherRecordsAndDrainsAlreadyQueuedEvents() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writer = new BinaryLogWriter(new BinaryLogProperties(true, directory, 1, 20), clock, json) {
            @Override BinaryLogFile openFile(Path path) throws IOException {
                entered.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("Timeout"); }
                catch (InterruptedException e) { throw new IOException(e); }
                return super.openFile(path);
            }
        };
        writer.start();
        try {
            assertTrue(writer.record(event(time)));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertTrue(writer.record(event(time.plusNanos(1))));
            assertFalse(writer.record(event(time.plusNanos(2))));
            assertFalse(writer.isHealthy());
            assertFalse(writer.record(event(time.plusNanos(3))));
        } finally { release.countDown(); writer.stop(); }
        assertEquals(2, records(writer.targetFile(time)).size());
    }
}
