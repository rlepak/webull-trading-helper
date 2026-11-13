package org.rl.webulltradinghelper.recording;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import tools.jackson.databind.ObjectMapper;

/** Worker-owned framed file. Validate before appending; repair only an incomplete final frame. */
final class BinaryLogFile implements AutoCloseable {
    static final int MAX_RECORD_BYTES = 16 * 1024 * 1024;
    private final FileChannel channel;
    private final DataOutputStream output;

    BinaryLogFile(Path path, ObjectMapper json) throws IOException {
        Files.createDirectories(path.getParent());
        try (var scan = new RandomAccessFile(path.toFile(), "rw")) {
            long validEnd = 0;
            while (scan.getFilePointer() < scan.length()) {
                if (scan.length() - scan.getFilePointer() < Integer.BYTES) {
                    recoverTail(scan, validEnd);
                    break;
                }
                int length = scan.readInt();
                if (length <= 0 || length > MAX_RECORD_BYTES) throw new IOException("Invalid binary record length");
                if (scan.length() - scan.getFilePointer() < length) {
                    recoverTail(scan, validEnd);
                    break;
                }
                byte[] bytes = new byte[length];
                scan.readFully(bytes);
                var record = json.readTree(bytes);
                if (record == null || record.path("schemaVersion").asInt() != 1
                        || !record.hasNonNull("recordingId") || !record.hasNonNull("sequenceNumber")
                        || !record.hasNonNull("receivedAt") || !record.path("event").isObject()) {
                    throw new IOException("Invalid binary record envelope");
                }
                try {
                    var received = java.time.OffsetDateTime.parse(record.path("receivedAt").asText()).toInstant();
                    var eventReceived = java.time.OffsetDateTime.parse(
                            record.path("event").path("receivedAt").asText()).toInstant();
                    if (!received.equals(eventReceived)) throw new IOException("Inconsistent binary receive times");
                } catch (java.time.DateTimeException invalidTime) {
                    throw new IOException("Invalid binary receive time");
                }
                validEnd = scan.getFilePointer();
            }
        }
        channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        output = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
    }

    private static void recoverTail(RandomAccessFile file, long validEnd) throws IOException {
        org.slf4j.LoggerFactory.getLogger(BinaryLogFile.class)
                .warn("Recovering incomplete binary recording tail discardedBytes={}", file.length() - validEnd);
        file.setLength(validEnd);
        file.getChannel().force(false);
    }

    void write(byte[] bytes) throws IOException {
        if (bytes.length < 1 || bytes.length > MAX_RECORD_BYTES) throw new IOException("Invalid binary record size");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    void force() throws IOException {
        output.flush();
        channel.force(false);
    }

    @Override public void close() throws IOException {
        try { force(); }
        finally { output.close(); }
    }
}
