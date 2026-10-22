package com.publicrecords.collection.cryptid;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single player's private Cryptid Cinema view — their assigned prompt, their own
 * submission, and the round timing they need to record against. Only ever sent to that
 * player.
 */
@Schema(description = "A player's private Cryptid Cinema view.")
public record CryptidPrivateView(
        @Schema(description = "brief | record | vote | results | finished") String phase,
        @Schema(description = "Current round, 1-based (0 before the first round).") int round,
        @Schema(description = "Total rounds in the episode.") int totalRounds,
        @Schema(description = "UNDYINGALOG | FOLEY | UNKNOWABLE_SFX | UFSFX | FOUNDTRACK") String roundType,
        @Schema(description = "Director's-notes line for the round.") String directorNotes,
        @Schema(description = "The prompt this player was assigned; null when the round has no prompt.") String prompt,
        @Schema(description = "Seconds: where this player's recording lands.") double recordStart,
        @Schema(description = "Seconds: end of the recording region.") double recordEnd,
        @Schema(description = "How many seconds this player should record.") double recordSeconds,
        @Schema(description = "Selectable options (choice rounds only; empty otherwise).") java.util.List<Choice> choices,
        @Schema(description = "This player's submission id this round, or null.") String submissionId,
        @Schema(description = "This player's uploaded recording id this round, or null.") String recordingId,
        @Schema(description = "The option label this player chose, or null.") String choiceLabel,
        @Schema(description = "Whether this player can still submit this round.") boolean canSubmit,
        @Schema(description = "Points the player scored this round (results only).") int roundScore,
        @Schema(description = "The player's cumulative score.") int totalScore) {

}
