package com.publicrecords.collection.getlit;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One submitted answer in a Get Lit round. During voting the {@code authorName} and
 * {@code points} are null — answers are anonymous until the round is scored.
 */
@Schema(description = "An anonymous (until scored) answer in a Get Lit round.")
public record AnswerView(
        @Schema(description = "Opaque answer id players vote on.") String id,
        @Schema(description = "The passage text.") String text,
        @Schema(description = "Author display name; null while anonymous.") String authorName,
        @Schema(description = "Points this answer earned; null until scored.") Integer points) {

}
