package com.publicrecords.collection.cryptid;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A selectable option in a Cryptid Cinema choice round — a sound effect (UFSFX) or a song
 * (FOUNDTRACK). Used both as episode content and as the option list shown to players.
 */
@Schema(description = "A selectable option in a Cryptid Cinema choice round.")
public record Choice(
        @Schema(description = "Stable option id.") String id,
        @Schema(description = "Label shown to players.") String label,
        @Schema(description = "Root-relative URL of the option's audio preview, when it has one; null otherwise.")
        String audioUrl) {

}
