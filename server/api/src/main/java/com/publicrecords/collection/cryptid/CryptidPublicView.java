package com.publicrecords.collection.cryptid;

import com.publicrecords.collection.getlit.ScoreEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The public view of a Cryptid Cinema game — everything the host's big screen shows
 * everyone, plus the round structure the host needs to drive the film.
 *
 * <p>Submissions are anonymous during {@code vote} ({@code playerName}/{@code points} null)
 * and revealed with their points in {@code results}. {@code scores} is cumulative and always
 * present; {@code roundScores} is populated only in {@code results}.</p>
 *
 * <p>{@code video}/{@code videoNoMusic} are Godot {@code res://} resource paths: the host
 * owns video playback, so only it resolves them.</p>
 */
@Schema(description = "The public (host-screen) view of a Cryptid Cinema game.")
public record CryptidPublicView(
        @Schema(description = "brief | record | vote | results | finished") String phase,
        @Schema(description = "Episode id.") String episodeId,
        @Schema(description = "Episode title.") String episodeTitle,
        @Schema(description = "VHS-label blurb for the episode.") String episodeBlurb,
        @Schema(description = "Film clip with its original music (host resource path).") String video,
        @Schema(description = "Film clip without music, for the foundtrack swap (host resource path).") String videoNoMusic,
        @Schema(description = "Current round, 1-based (0 before the first round).") int round,
        @Schema(description = "Total rounds in the episode.") int totalRounds,
        @Schema(description = "UNDYINGALOG | FOLEY | UNKNOWABLE_SFX | UFSFX | FOUNDTRACK") String roundType,
        @Schema(description = "Seconds: clip the players watch from.") double seeStart,
        @Schema(description = "Seconds: clip the players watch to.") double seeEnd,
        @Schema(description = "Seconds: where the recorded/chosen audio lands.") double recordStart,
        @Schema(description = "Seconds: end of the recorded/chosen region.") double recordEnd,
        @Schema(description = "Director's-notes line for the round.") String directorNotes,
        @Schema(description = "Prompt pool for the round (empty for non-prompt rounds).") List<String> prompts,
        @Schema(description = "Selectable options (choice rounds only; empty otherwise).") List<Choice> choices,
        @Schema(description = "How many players have submitted this round.") int submitted,
        @Schema(description = "Number of players in the game.") int players,
        @Schema(description = "The round's submissions (anonymous until results).") List<CryptidSubmissionView> submissions,
        @Schema(description = "Points awarded this round, highest first (results only).") List<ScoreEntry> roundScores,
        @Schema(description = "Cumulative scores, highest first.") List<ScoreEntry> scores) {

}
