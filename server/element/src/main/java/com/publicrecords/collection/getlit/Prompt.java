package com.publicrecords.collection.getlit;

/**
 * Internal view of a Get Lit prompt, parsed from the packaged
 * {@code content/get-lit/prompts.json}.
 */
record Prompt(String id, String text, boolean isSpicy, boolean isActive) {

    boolean allowed(final boolean familyFriendly) {
        return isActive && (!familyFriendly || !isSpicy);
    }

}
