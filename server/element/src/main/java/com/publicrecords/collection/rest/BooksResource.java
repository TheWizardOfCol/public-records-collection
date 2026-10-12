package com.publicrecords.collection.rest;

import com.publicrecords.collection.getlit.PassageView;
import com.publicrecords.collection.getlit.WorkView;
import com.publicrecords.collection.service.LibraryService;
import dev.getelements.elements.sdk.ElementSupplier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

import static com.publicrecords.collection.CollectionApplication.OPENAPI_TAG;

/**
 * The Get Lit content library: the works a player can choose from, and each work's full text
 * served as paginated passages. Used by the player controller (to pick a work and then select
 * a passage) and by the host (to display a player's chosen work, if needed).
 */
@Tag(name = OPENAPI_TAG)
@Path("/books")
public class BooksResource {

    private final LibraryService library =
            ElementSupplier.getElementLocal(BooksResource.class).get()
                    .getServiceLocator().getInstance(LibraryService.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "List works",
            description = "The public-domain works players can choose from, optionally filtered for family-friendliness."
    )
    @ApiResponse(responseCode = "200", description = "The list of works")
    public List<WorkView> list(
            @Parameter(description = "When true, spicy/NSFW works are filtered out")
            @QueryParam("familyFriendly") @DefaultValue("true") final boolean familyFriendly) {
        return library.works(familyFriendly);
    }

    @GET
    @Path("/{id}/text")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Read a passage of a work",
            description = "Returns a slice of the work's full text. Page through by passing the returned "
                    + "offset + length until last=true."
    )
    @ApiResponse(responseCode = "200", description = "The passage",
            content = @Content(schema = @Schema(implementation = PassageView.class)))
    @ApiResponse(responseCode = "404", description = "No work with that id")
    public Response text(
            @Parameter(description = "Work id (e.g. book-0001)") @PathParam("id") final String id,
            @Parameter(description = "Starting character offset") @QueryParam("offset") @DefaultValue("0") final int offset,
            @Parameter(description = "Maximum characters to return") @QueryParam("limit") @DefaultValue("1500") final int limit) {
        return library.passage(id, offset, limit)
                .<Response>map(passage -> Response.ok(passage).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "no such work"))
                        .build());
    }

}
