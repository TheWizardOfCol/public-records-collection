package com.publicrecords.collection.rest;

import com.publicrecords.collection.lobby.CreatedRoom;
import com.publicrecords.collection.service.LobbyService;
import dev.getelements.elements.sdk.ElementSupplier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

import static com.publicrecords.collection.CollectionApplication.OPENAPI_TAG;

/**
 * REST surface for the lobby: enough for the host to open a room and for any client to
 * read a snapshot. Everything players actually do once connected flows over the lobby
 * WebSocket at {@code /collection-ws/lobby}.
 *
 * <p>Open by design — guest play means no Elements account is required — so a room code
 * plus the host token are the only capabilities that matter here.</p>
 */
@Tag(name = OPENAPI_TAG)
@Path("/rooms")
public class RoomsResource {

    private final LobbyService lobby =
            ElementSupplier.getElementLocal(RoomsResource.class).get()
                    .getServiceLocator().getInstance(LobbyService.class);

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Open a lobby room",
            description = "Host-only. Creates a room and returns its join code plus the secret host token "
                    + "used to act as host over the WebSocket."
    )
    @ApiResponse(responseCode = "200", description = "The new room's code and host token",
            content = @Content(schema = @Schema(implementation = CreatedRoom.class)))
    public CreatedRoom create(
            @Parameter(description = "Display name for the host connection")
            @QueryParam("hostName") final String hostName) {
        return lobby.createRoom(hostName);
    }

    @GET
    @Path("/{code}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Read a lobby room",
            description = "Snapshot of a room by code: phase, settings, and everyone currently attached."
    )
    @ApiResponse(responseCode = "200", description = "The room snapshot")
    @ApiResponse(responseCode = "404", description = "No room with that code")
    public Response get(
            @Parameter(description = "Room code, case-insensitive")
            @PathParam("code") final String code) {
        return lobby.getRoom(code)
                .<Response>map(room -> Response.ok(room).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "no such room"))
                        .build());
    }

}
