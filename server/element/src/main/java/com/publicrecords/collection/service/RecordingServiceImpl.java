package com.publicrecords.collection.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.publicrecords.collection.media.RecordingStore;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;
import dev.getelements.elements.sdk.annotation.ElementServiceImplementation;

import java.util.Optional;

/**
 * Thin exported facade over {@link RecordingStore}, so the recordings REST endpoints can
 * reach the take store through the service locator (which only resolves exported services).
 */
@Singleton
@ElementServiceImplementation
@ElementServiceExport(RecordingService.class)
public class RecordingServiceImpl implements RecordingService {

    private final RecordingStore store;

    @Inject
    public RecordingServiceImpl(final RecordingStore store) {
        this.store = store;
    }

    @Override
    public Take store(final byte[] bytes, final String contentType) {
        return toTake(store.store(bytes, contentType));
    }

    @Override
    public Optional<Take> get(final String id) {
        return store.get(id).map(RecordingServiceImpl::toTake);
    }


    @Override
    public String startChunked(final int totalChunks, final int expectedSize) {
        return store.startChunked(totalChunks, expectedSize);
    }

    @Override
    public void addChunk(final String uploadId, final int index, final byte[] bytes) {
        store.addChunk(uploadId, index, bytes);
    }

    @Override
    public Take finishChunked(final String uploadId, final String contentType) {
        return toTake(store.finishChunked(uploadId, contentType));
    }


    private static Take toTake(final RecordingStore.Recording recording) {
        return new Take(recording.id(), recording.contentType(), recording.bytes());
    }

}
