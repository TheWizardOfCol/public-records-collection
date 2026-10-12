package com.publicrecords.collection.service;

import com.publicrecords.collection.getlit.PassageView;
import com.publicrecords.collection.getlit.WorkView;

import java.util.List;
import java.util.Optional;

/**
 * Exported read-only access to the Element's content library: the list of works players can
 * choose from, and each work's full text paged as passages.
 *
 * <p>Resolved from the REST resource via the Elements service locator.</p>
 */
public interface LibraryService {

    /**
     * @param familyFriendly when true, spicy/NSFW works are filtered out
     * @return the selectable works, in a stable order
     */
    List<WorkView> works(boolean familyFriendly);

    /**
     * Reads a slice of a work's normalized full text.
     *
     * @param bookId the work id
     * @param offset starting character offset (clamped to the text length)
     * @param limit  maximum characters to return (clamped to a sane range)
     * @return the passage, or empty if no such work exists
     */
    Optional<PassageView> passage(String bookId, int offset, int limit);

}
