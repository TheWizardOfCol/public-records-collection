package com.publicrecords.collection.cryptid;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One player's contribution to a Cryptid Cinema round — either a recording they uploaded
 * (the {@code audioUrl} points at it) or a choice they picked from a supplied library.
 * During voting the {@code playerName} and {@code points} are null; submissions are
 * anonymous until the round is scored.
 */
@Schema(description = "A player's submission in a Cryptid Cinema round.")
public record CryptidSubmissionView(
        @Schema(description = "Opaque submission id.") String id,
        @Schema(description = "Player id.") String playerId,
        @Schema(description = "Author display name; null while anonymous.") String playerName,
        @Schema(description = "recording | choice") String kind,
        @Schema(description = "Chosen option label (choice rounds); null for recordings.") String label,
        @Schema(description = "Chosen option id (choice rounds); null for recordings. The host maps this to its own asset.") String choiceId,
        @Schema(description = "Uploaded recording id (recording rounds); null for choices.") String recordingId,
        @Schema(description = "Relative URL to fetch the submission's audio (recording, or a choice's preview); null when it has none.") String audioUrl,
        @Schema(description = "Points this submission earned; null until scored.") Integer points) {

}
