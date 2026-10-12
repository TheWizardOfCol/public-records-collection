package com.publicrecords.collection.lobby;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A participant in a lobby room, as seen by every client. The host connection is
 * reported with {@code host=true} and the reserved id {@code "host"} so clients can
 * render it distinctly (the host display isn't itself a player).
 */
@Schema(description = "A lobby room participant.")
public record PlayerView(
        @Schema(description = "Opaque, stable player id for the lifetime of the room.") String id,
        @Schema(description = "Display name shown to everyone.") String name,
        @Schema(description = "True for the host/control connection.") boolean host,
        @Schema(description = "False while this participant's socket is disconnected.") boolean connected) {

}
