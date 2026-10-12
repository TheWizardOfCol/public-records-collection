package com.publicrecords.collection.rest;

import com.publicrecords.collection.service.CollectionService;
import dev.getelements.elements.sdk.Element;
import dev.getelements.elements.sdk.ElementSupplier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import static com.publicrecords.collection.CollectionApplication.OPENAPI_TAG;

@Tag(name = OPENAPI_TAG)
@Path("/health")
public class Health {

    private final Element element = ElementSupplier.getElementLocal(Health.class).get();

    private final CollectionService collectionService =
            element.getServiceLocator().getInstance(CollectionService.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Health probe",
            description = "Returns the Collection Element's identity and liveness."
    )
    public HealthStatus getHealth() {
        return new HealthStatus("ok", collectionService.getElementId());
    }

    public record HealthStatus(String status, String element) {}

}
