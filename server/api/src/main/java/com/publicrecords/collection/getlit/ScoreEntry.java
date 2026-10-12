package com.publicrecords.collection.getlit;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A player's score in a Get Lit game — either their points for the round just scored, or
 * their cumulative total. Lists are sorted highest-first for direct rendering.
 */
@Schema(description = "A player's Get Lit score.")
public record ScoreEntry(
        @Schema(description = "Player id.") String playerId,
        @Schema(description = "Player display name.") String name,
        @Schema(description = "Score value.") int score) {

}
