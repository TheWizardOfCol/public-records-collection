package com.publicrecords.collection.getlit;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A work (public-domain book) in the Get Lit library, as offered to a player choosing what
 * to answer from. Only the fields a player needs to make a choice are exposed; the full
 * text is fetched separately.
 */
@Schema(description = "A selectable work in the Get Lit library.")
public record WorkView(
        @Schema(description = "Stable work id (e.g. book-0001).") String id,
        @Schema(description = "Title shown to players.") String title,
        @Schema(description = "Author.") String author) {

}
