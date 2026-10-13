package com.publicrecords.collection.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.publicrecords.collection.getlit.GetLitContent;
import com.publicrecords.collection.getlit.PassageView;
import com.publicrecords.collection.getlit.WorkView;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;
import dev.getelements.elements.sdk.annotation.ElementServiceImplementation;

import java.util.List;
import java.util.Optional;

/**
 * Thin exported facade over {@link GetLitContent}, so the REST library endpoints can reach
 * the content through the service locator (which only resolves exported services).
 */
@Singleton
@ElementServiceImplementation
@ElementServiceExport(LibraryService.class)
public class LibraryServiceImpl implements LibraryService {

    private final GetLitContent content;

    @Inject
    public LibraryServiceImpl(final GetLitContent content) {
        this.content = content;
    }

    @Override
    public List<WorkView> works(final boolean familyFriendly) {
        return content.works(familyFriendly);
    }

    @Override
    public Optional<PassageView> passage(final String bookId, final int offset, final int limit) {
        return content.passage(bookId, offset, limit);
    }

}
