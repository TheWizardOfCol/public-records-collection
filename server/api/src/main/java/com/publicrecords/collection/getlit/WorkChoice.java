package com.publicrecords.collection.getlit;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a single player has chosen as their work, for the host's big-screen view. The title
 * is null until that player has picked.
 */
@Schema(description = "A player's chosen work, for the public view.")
public record WorkChoice(
        @Schema(description = "Player id.") String playerId,
        @Schema(description = "Player display name.") String playerName,
        @Schema(description = "Chosen work id, or null if not yet chosen.") String workId,
        @Schema(description = "Chosen work title, or null if not yet chosen.") String title) {

}
