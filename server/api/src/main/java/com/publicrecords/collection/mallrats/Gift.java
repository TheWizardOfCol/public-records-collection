package com.publicrecords.collection.mallrats;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One gift for sale in a Mall Rats store. Gift art ships with the pack; the
 * editorial name is whatever the designer titled the image file, shown under
 * the picture in the shopping grid.
 */
@Schema(description = "A Mall Rats gift.")
public record Gift(
        @Schema(description = "Stable gift id (slug of its filename).") String id,
        @Schema(description = "Display name of the gift.") String name,
        @Schema(description = "Served path of the gift's picture.") String image) {

}
