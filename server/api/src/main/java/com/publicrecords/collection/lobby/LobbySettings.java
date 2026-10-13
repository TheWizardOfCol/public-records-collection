package com.publicrecords.collection.lobby;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lobby-wide settings the host controls before a game starts. Values are clamped
 * on construction so an out-of-range request can never produce an unusable room.
 */
@Schema(description = "Host-controlled, lobby-wide settings.")
public record LobbySettings(
        @Schema(description = "When true, spicy/NSFW content is filtered out of the games.")
        boolean familyFriendly,
        @Schema(description = "Per-question answer time limit, in seconds.", example = "30")
        int answerTimerSeconds,
        @Schema(description = "Number of rounds a game plays.", example = "3")
        int rounds) {

    public static final int MIN_TIMER_SECONDS = 5;
    public static final int MAX_TIMER_SECONDS = 300;
    public static final int MIN_ROUNDS = 1;
    public static final int MAX_ROUNDS = 10;

    public LobbySettings {
        answerTimerSeconds = Math.max(MIN_TIMER_SECONDS, Math.min(MAX_TIMER_SECONDS, answerTimerSeconds));
        rounds = Math.max(MIN_ROUNDS, Math.min(MAX_ROUNDS, rounds));
    }

    public static LobbySettings defaults() {
        return new LobbySettings(true, 30, 3);
    }

}
