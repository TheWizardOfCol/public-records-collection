package com.publicrecords.collection.lobby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Shared JSON and WebSocket send helpers for the lobby.
 *
 * <p>One {@link ObjectMapper} for the whole Element, and a single send path that
 * synchronizes on the session. Updates fan out from several connections' message
 * threads, and Jetty's basic remote is not safe for interleaved concurrent writes to
 * the same session — the lock is what keeps a single client's frames intact.</p>
 */
public final class Wire {

    private static final Logger logger = LoggerFactory.getLogger(Wire.class);

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Wire() {
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static JsonNode value(final Object value) {
        return MAPPER.valueToTree(value);
    }

    public static JsonNode tree(final String json) throws IOException {
        return MAPPER.readTree(json);
    }

    public static void send(final Session session, final ObjectNode message) {
        try {
            synchronized (session) {
                session.getBasicRemote().sendText(MAPPER.writeValueAsString(message));
            }
        } catch (final Exception e) {
            logger.debug("send to session {} failed: {}", session.getId(), e.getMessage());
        }
    }

}
