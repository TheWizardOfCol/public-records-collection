package com.publicrecords.collection.mallrats;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A gift as the shopping grid shows it. {@code avgStars} is null until the
 * results reveal — during rating/shopping the pool's ratings stay secret so
 * nobody shops with inside information.
 */
@Schema(description = "A gift in a Mall Rats round's pool.")
public record MallRatsPoolGift(
        @Schema(description = "Gift id.") String id,
        @Schema(description = "Display name.") String name,
        @Schema(description = "Served path of the picture.") String image,
        @Schema(description = "Average rating stars (null before the reveal).") Double avgStars) {

}
