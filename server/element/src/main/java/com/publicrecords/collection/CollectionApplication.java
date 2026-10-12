package com.publicrecords.collection;

import com.publicrecords.collection.rest.BooksResource;
import com.publicrecords.collection.rest.Health;
import com.publicrecords.collection.rest.RoomsResource;
import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;
import dev.getelements.elements.sdk.annotation.ElementServiceImplementation;
import jakarta.ws.rs.core.Application;

import java.util.Set;

@ElementServiceImplementation
@ElementServiceExport(Application.class)
public class CollectionApplication extends Application {

    /**
     * Platform auth filter. Disabled for now: players join with a name and room
     * code rather than an Elements account (guest play), so the game's REST and
     * WebSocket surfaces stay open. Flip to "true" once the game also talks to
     * authenticated platform APIs (e.g. persisted leaderboards).
     */
    @ElementDefaultAttribute("false")
    public static final String AUTH_ENABLED = "dev.getelements.elements.auth.enabled";

    /** Mount root of this Element's REST endpoints → /collection/health, ... */
    @ElementDefaultAttribute("/collection")
    public static final String RS_ROOT = "dev.getelements.elements.element.rs.root";

    /**
     * WebSocket base path → /collection-ws. Deliberately a sibling of {@link #RS_ROOT},
     * not nested under it: the platform dispatches REST and WebSocket handlers from one
     * ordered sequence (REST first), so an RS context at /collection would swallow any
     * /collection/... WebSocket path before the WebSocket handler is consulted.
     */
    @ElementDefaultAttribute("/collection-ws")
    public static final String WS_ROOT = "dev.getelements.elements.element.ws.root";

    /** Static web content (the player controller app), served under /app/static/collection/. */
    @ElementDefaultAttribute("/app/static/collection")
    public static final String STATIC_CONTENT_URI = "dev.getelements.element.static.uri";

    /** Dashboard UI plugin content, served under /app/ui/collection/. */
    @ElementDefaultAttribute("/app/ui/collection")
    public static final String UI_CONTENT_URI = "dev.getelements.element.ui.uri";

    public static final String OPENAPI_TAG = "Collection";

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(
                // Endpoints
                Health.class,
                RoomsResource.class,
                BooksResource.class,

                // Declares the builtin Elements auth scheme for the generated OpenAPI document.
                OpenAPISecurityConfig.class
        );
    }

}
