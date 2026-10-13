package com.publicrecords.collection.service;

/**
 * Exported entry point for The Public Records Collection Element.
 *
 * <p>The party pack's room, lobby, and per-game services will be exported
 * alongside this interface as the shared platform is built out. For now it
 * exposes only the Element's own identity so the wiring (Guice module, service
 * locator, REST endpoint) can be exercised end to end.</p>
 */
public interface CollectionService {

    /**
     * @return this Element's stable identifier
     */
    String getElementId();

}
