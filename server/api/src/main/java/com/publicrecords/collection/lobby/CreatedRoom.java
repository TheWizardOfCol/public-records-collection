package com.publicrecords.collection.lobby;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned when the host opens a new room. {@code hostToken} is a secret: it is what
 * authorizes host actions (starting a game, changing settings, closing the room) over
 * the WebSocket, and must be kept away from players.
 */
@Schema(description = "A freshly created room's join code and secret host token.")
public record CreatedRoom(
        @Schema(description = "Room code to share with players.") String code,
        @Schema(description = "Secret token the host presents over the WebSocket to act as host.") String hostToken) {

}
