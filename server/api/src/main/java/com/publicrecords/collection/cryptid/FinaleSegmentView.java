package com.publicrecords.collection.cryptid;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One piece of the grand-finale screening: where the film rolls, where the
 * original sound drops out and play comes back, which winning submission lays
 * over that window, and — for the foundtrack round — the record's song.
 */
@Schema(description = "A segment of the Cryptid Cinema grand screening.")
public record FinaleSegmentView(
        @Schema(description = "Round number this segment belongs to (1-based).") int round,
        @Schema(description = "Screening mode for the segment: window | audio | song.") String mode,
        @Schema(description = "Seconds: film position where the piece begins.") double start,
        @Schema(description = "Seconds: film position where the piece ends (0 for song/whole-film).") double end,
        @Schema(description = "Seconds between layered starts for the same window.") double stagger,
        @Schema(description = "The winning submissions, in vote order.") List<CryptidSubmissionView> winners,
        @Schema(description = "The recording to start at this window (first winner).") String audioUrl,
        @Schema(description = "Winning display name for the overlay, when useful.") String winnerName) {

}
