package com.publicrecords.collection.rest;

import com.publicrecords.collection.media.RecordingStore;
import com.publicrecords.collection.service.RecordingService;
import dev.getelements.elements.sdk.ElementSupplier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

import static com.publicrecords.collection.CollectionApplication.OPENAPI_TAG;

/**
 * Upload/download surface for player audio takes. A player records in the browser
 * (MediaRecorder), POSTs the blob here, and gets back an id; the id is then submitted over
 * the lobby WebSocket, and the host fetches the bytes from the GET endpoint to play them
 * against the film.
 *
 * <p>Open like the rest of the lobby (guest play), and ephemeral — takes live in memory
 * for the session (see {@link RecordingStore}).</p>
 */
@Tag(name = OPENAPI_TAG)
@Path("/recordings")
public class RecordingsResource {

    private final RecordingService recordings =
            ElementSupplier.getElementLocal(RecordingsResource.class).get()
                    .getServiceLocator().getInstance(RecordingService.class);

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Upload a recorded take",
            description = "Stores the raw audio in the request body and returns its id and fetch URL."
    )
    @ApiResponse(responseCode = "200", description = "The stored recording's id and URL")
    public Response upload(
            @Context final HttpHeaders headers,
            final byte[] body) {
        final MediaType type = headers.getMediaType();
        final RecordingService.Take recording =
                recordings.store(body, type == null ? null : type.toString());
        return Response.ok(Map.of(
                "id", recording.id(),
                "url", "/collection/recordings/" + recording.id())).build();
    }

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Fetch a recorded take",
            description = "Returns the stored audio bytes, so the host can play a submission."
    )
    @ApiResponse(responseCode = "200", description = "The recording audio")
    @ApiResponse(responseCode = "404", description = "No recording with that id")
    public Response download(
            @Parameter(description = "Recording id returned by the upload")
            @PathParam("id") final String id) {
        return recordings.get(id)
                .<Response>map(recording -> Response.ok(recording.bytes())
                        .type(recording.contentType())
                        .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }


    @POST
    @Path("/chunked/start")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startChunked(final Map<String, Object> req) {
        final int totalChunks = ((Number) req.getOrDefault("totalChunks", 0)).intValue();
        final int expectedSize = ((Number) req.getOrDefault("expectedSize", 0)).intValue();
        final String contentType = req.getOrDefault("contentType", "audio/wav").toString();
        final String uploadId = recordings.startChunked(totalChunks, expectedSize);
        return Response.ok(Map.of("uploadId", uploadId, "contentType", contentType)).build();
    }

    @POST
    @Path("/chunked/{uploadId}/{index}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addChunk(
            @PathParam("uploadId") final String uploadId,
            @PathParam("index") final int index,
            final byte[] body) {
        recordings.addChunk(uploadId, index, body);
        return Response.ok().build();
    }

    @POST
    @Path("/chunked/{uploadId}/finish")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response finishChunked(
            @PathParam("uploadId") final String uploadId,
            @Context final HttpHeaders headers) {
        final MediaType type = headers.getMediaType();
        final RecordingService.Take recording = recordings.finishChunked(uploadId,
                type == null ? null : type.toString());
        return Response.ok(Map.of(
                "id", recording.id(),
                "url", "/collection/recordings/" + recording.id())).build();
    }

}
