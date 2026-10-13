package com.publicrecords.collection.getlit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A single player's private Get Lit view — their work, their own submission, and their vote
 * state, plus the shared public fields they need to render. Only ever sent to that player.
 */
@Schema(description = "A player's private Get Lit view.")
public record GetLitPrivateView(
        @Schema(description = "select | answer | vote | results | finished") String phase,
        @Schema(description = "Current round, 1-based (0 before the first round).") int round,
        @Schema(description = "Total rounds in the game.") int totalRounds,
        @Schema(description = "The round's prompt text; null outside answer/vote/results.") String prompt,
        @Schema(description = "Selectable works (select phase only; empty otherwise).") List<WorkView> library,
        @Schema(description = "The player's chosen work id, or null.") String workId,
        @Schema(description = "The player's chosen work title, or null.") String workTitle,
        @Schema(description = "The player's submitted answer id this round, or null.") String answerId,
        @Schema(description = "The player's submitted passage this round, or null.") String answer,
        @Schema(description = "Whether the player can still spend their golden heart this round.") boolean canGolden,
        @Schema(description = "Answer ids this player has already hearted this round.") List<String> hearted,
        @Schema(description = "Points the player scored this round (results only).") int roundScore,
        @Schema(description = "The player's cumulative score.") int totalScore) {

}
