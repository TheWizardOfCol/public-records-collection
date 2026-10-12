package com.publicrecords.collection.lobby;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A point-in-time snapshot of a lobby room. The server serializes this same shape
 * both over REST and in the {@code state}/{@code welcome} WebSocket messages, so the
 * host display and the browser controllers stay in lockstep.
 */
@Schema(description = "A point-in-time snapshot of a lobby room.")
public record RoomView(
        @Schema(description = "Four-character room code players type to join.") String code,
        @Schema(description = "Lifecycle phase: lobby | starting | in-progress | results.") String phase,
        @Schema(description = "Chosen game id once the host starts one; null in the lobby.") String gameId,
        @Schema(description = "Host-controlled lobby settings.") LobbySettings settings,
        @Schema(description = "Everyone attached to the room, host first.") List<PlayerView> players) {

}
