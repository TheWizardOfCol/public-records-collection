package com.publicrecords.collection.service;

import java.util.Optional;

/**
 * Exported facade over the in-memory store of player audio takes, so the recordings REST
 * endpoints can reach it through the service locator — which resolves only exported
 * services (see {@code LibraryService} for the same pattern).
 */
public interface RecordingService {

    /**
     * Store a take's raw bytes and return its handle.
     *
     * @throws IllegalArgumentException if the bytes are null/empty or over the per-take cap
     * @throws IllegalStateException if the store is at capacity
     */
    Take store(byte[] bytes, String contentType);

    Optional<Take> get(String id);

    /** A stored take: its id, the content type it was uploaded with, and its raw bytes. */

    String startChunked(int totalChunks, int expectedSize);

    void addChunk(String uploadId, int index, byte[] bytes);

    Take finishChunked(String uploadId, String contentType);


    record Take(String id, String contentType, byte[] bytes) {
    }

}
