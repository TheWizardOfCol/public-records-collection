package com.publicrecords.collection.getlit;

/**
 * Internal view of a library book, parsed from the packaged {@code content/get-lit/books.json}.
 */
record Book(
        String id,
        String title,
        String author,
        String category,
        boolean isSpicy,
        boolean isActive,
        String slug) {

    boolean allowed(final boolean familyFriendly) {
        return isActive && (!familyFriendly || !isSpicy);
    }

}
