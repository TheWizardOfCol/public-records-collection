package com.publicrecords.collection.media;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * In-memory store for player audio takes uploaded during a Cryptid Cinema round.
 *
 * <p>Deliberately in-process and ephemeral, matching the lobby: a party's takes only need
 * to survive the session, and rooms are in-memory too. The caps below keep an errant or
 * hostile client from exhausting the heap — a real take is a few seconds of Opus and lands
 * in the tens of kilobytes.</p>
 */
@Singleton
public class RecordingStore {

    private static final Logger logger = LoggerFactory.getLogger(RecordingStore.class);

    private static final long MAX_RECORDING_BYTES = 16L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    private final Map<String, Recording> recordings = new ConcurrentHashMap<>();

    private long totalBytes = 0;

    public Optional<Recording> get(final String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(recordings.get(id));
    }

    public Recording store(final byte[] bytes, final String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("empty recording");
        }
        if (bytes.length > MAX_RECORDING_BYTES) {
            throw new IllegalArgumentException("recording too large");
        }
        synchronized (this) {
            if (totalBytes + bytes.length > MAX_TOTAL_BYTES) {
                throw new IllegalStateException("recording storage full");
            }
            final String id = UUID.randomUUID().toString();
            final String type = (contentType == null || contentType.isBlank())
                    ? "application/octet-stream" : contentType;
            final Recording recording = new Recording(id, type, bytes);
            recordings.put(id, recording);
            totalBytes += bytes.length;
            logger.info("stored recording {} ({} bytes, {}), {} total", id, bytes.length, type, totalBytes);
            return recording;
        }
    }

    public record Recording(String id, String contentType, byte[] bytes) {

    }


    private final Map<String, ChunkedUpload> chunks = new ConcurrentHashMap<>();

    public static class ChunkedUpload {
        final String id;
        final int totalChunks;
        final int expectedSize;
        final byte[][] parts;
        int received;
        long started = System.currentTimeMillis();

        ChunkedUpload(final String id, final int totalChunks, final int expectedSize) {
            this.id = id;
            this.totalChunks = totalChunks;
            this.expectedSize = expectedSize;
            this.parts = new byte[totalChunks][];
            this.received = 0;
        }
    }

    public String startChunked(final int totalChunks, final int expectedSize) {
        final String id = UUID.randomUUID().toString();
        chunks.put(id, new ChunkedUpload(id, totalChunks, expectedSize));
        return id;
    }

    public synchronized void addChunk(final String uploadId, final int index, final byte[] bytes) {
        final ChunkedUpload upload = chunks.get(uploadId);
        if (upload == null) throw new IllegalArgumentException("unknown upload");
        if (index < 0 || index >= upload.parts.length) throw new IllegalArgumentException("invalid chunk index");
        if (upload.parts[index] != null) throw new IllegalArgumentException("chunk already received");
        upload.parts[index] = bytes;
        upload.received++;
    }

    public synchronized Recording finishChunked(final String uploadId, final String contentType) {
        final ChunkedUpload upload = chunks.remove(uploadId);
        if (upload == null) throw new IllegalArgumentException("unknown upload");
        if (upload.received != upload.parts.length) throw new IllegalArgumentException("incomplete upload");
        int total = 0;
        for (byte[] p : upload.parts) total += p.length;
        if (total > MAX_RECORDING_BYTES) throw new IllegalArgumentException("recording too large");
        if (upload.expectedSize > 0 && total != upload.expectedSize) throw new IllegalArgumentException("size mismatch");
        final byte[] combined = new byte[total];
        int pos = 0;
        for (byte[] p : upload.parts) {
            System.arraycopy(p, 0, combined, pos, p.length);
            pos += p.length;
        }
        if (totalBytes + total > MAX_TOTAL_BYTES) {
            throw new IllegalStateException("recording storage full");
        }
        final String id = UUID.randomUUID().toString();
        final String type = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        final Recording recording = new Recording(id, type, combined);
        recordings.put(id, recording);
        totalBytes += total;
        logger.info("stored chunked recording {} ({} bytes, {}), {} total", id, total, type, totalBytes);
        return recording;
    }

}
