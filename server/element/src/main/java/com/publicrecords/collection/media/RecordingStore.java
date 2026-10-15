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

}
