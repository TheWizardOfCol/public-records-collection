package com.publicrecords.collection;

import dev.getelements.elements.sdk.model.Headers;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;

import static dev.getelements.elements.sdk.jakarta.rs.AuthSchemes.SESSION_SECRET;
import static io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER;
import static io.swagger.v3.oas.annotations.enums.SecuritySchemeType.APIKEY;

@OpenAPIDefinition(
        info = @Info(
                title = "The Public Records Collection",
                description = "Server-authoritative party pack. Hosts a realtime lobby and game state; "
                        + "players join from the browser with a room code.",
                contact = @Contact(
                        url = "https://github.com/TheWizardOfCol/public-records-collection",
                        name = "The Public Records Collection"
                )
        ),
        externalDocs = @ExternalDocumentation(
                url = "https://github.com/NamazuStudios/elements-manual",
                description = "See the Namazu Elements Manual for platform details."
        ),
        security = {
                @SecurityRequirement(name = SESSION_SECRET)
        }
)
@SecuritySchemes({
        @SecurityScheme(
                type = APIKEY,
                in = HEADER,
                name = SESSION_SECRET,
                paramName = SESSION_SECRET,
                description = "Session secret required for authenticated endpoints")
})
public class OpenAPISecurityConfig {}
