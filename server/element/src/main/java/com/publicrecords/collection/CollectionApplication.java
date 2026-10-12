package com.publicrecords.collection;

import com.publicrecords.collection.rest.Health;
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

    /** WebSocket base path → /collection/ws (lobby + realtime game state). */
    @ElementDefaultAttribute("/collection/ws")
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

                // Declares the builtin Elements auth scheme for the generated OpenAPI document.
                OpenAPISecurityConfig.class
        );
    }

}
