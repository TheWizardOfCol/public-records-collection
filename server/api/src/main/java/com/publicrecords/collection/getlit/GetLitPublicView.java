package com.publicrecords.collection.getlit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The public view of a Get Lit game — everything the host's big screen shows everyone.
 *
 * <p>Answers are anonymous during {@code vote} ({@code authorName}/{@code points} null) and
 * revealed with their points in {@code results}. {@code scores} is cumulative and always
 * present; {@code roundScores} is populated only in {@code results}.</p>
 */
@Schema(description = "The public (host-screen) view of a Get Lit game.")
public record GetLitPublicView(
        @Schema(description = "select | answer | vote | results | finished") String phase,
        @Schema(description = "Current round, 1-based (0 before the first round).") int round,
        @Schema(description = "Total rounds in the game.") int totalRounds,
        @Schema(description = "The round's prompt text; null outside answer/vote/results.") String prompt,
        @Schema(description = "Each player's chosen work (title null until chosen).") List<WorkChoice> works,
        @Schema(description = "How many players have submitted an answer this round.") int submitted,
        @Schema(description = "Number of players in the game.") int players,
        @Schema(description = "The round's answers (anonymous until results).") List<AnswerView> answers,
        @Schema(description = "Points awarded this round, highest first (results only).") List<ScoreEntry> roundScores,
        @Schema(description = "Cumulative scores, highest first.") List<ScoreEntry> scores) {

}
