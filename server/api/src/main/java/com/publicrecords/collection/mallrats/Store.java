package com.publicrecords.collection.mallrats;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A Mall Rats store: a named set of gifts the host shops at for the whole
 * game, displayed with its price meter ($, $$, $$$, ∞).
 */
@Schema(description = "A Mall Rats store.")
public record Store(
        @Schema(description = "Store id.") String id,
        @Schema(description = "Store name (as on the sign).") String name,
        @Schema(description = "Price meter: $ | $$ | $$$ | ∞.") String meter,
        @Schema(description = "The store's gifts.") List<Gift> gifts) {

}
