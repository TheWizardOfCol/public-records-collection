package com.publicrecords.collection.getlit;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A slice of a book's full text. Clients page through a work one passage at a time
 * (design decision: players see the full text, delivered incrementally) by following
 * {@code offset} until {@code last} is true.
 */
@Schema(description = "A passage (slice) of a work's full text.")
public record PassageView(
        @Schema(description = "Work id.") String bookId,
        @Schema(description = "Work title.") String title,
        @Schema(description = "Work author.") String author,
        @Schema(description = "Character offset this passage starts at.") int offset,
        @Schema(description = "Character length of this passage.") int length,
        @Schema(description = "Total characters in the work's normalized text.") int totalLength,
        @Schema(description = "True when this passage reaches the end of the work.") boolean last,
        @Schema(description = "The passage text.") String text) {

}
