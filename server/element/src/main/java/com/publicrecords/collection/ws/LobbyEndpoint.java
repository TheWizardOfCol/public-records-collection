package com.publicrecords.collection.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.publicrecords.collection.lobby.LobbyException;
import com.publicrecords.collection.lobby.LobbySettings;
import com.publicrecords.collection.service.LobbyService;
import com.publicrecords.collection.lobby.Wire;
import dev.getelements.elements.sdk.ElementSupplier;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The lobby WebSocket — mounted at {@code /collection-ws/lobby} (this Element's WS root
 * plus the {@link #PATH}) and discovered by the deployment's {@code @ServerEndpoint} scan.
 *
 * <p>One JSON message per frame. Clients open a socket and speak one of:</p>
 * <ul>
 *   <li>{@code {"type":"host","code":"ABCD","token":"…"}} — the host display claims its room</li>
 *   <li>{@code {"type":"join","code":"ABCD","name":"Alice"}} — a player takes a seat</li>
 *   <li>{@code {"type":"start","gameId":"get-lit"}} — host starts a game (host only)</li>
 *   <li>{@code {"type":"settings","settings":{…}}} — host updates lobby settings (host only)</li>
 *   <li>{@code {"type":"leave"}} — host closes the room</li>
 *   <li>{@code {"type":"ping"}} — keepalive</li>
 * </ul>
 *
 * <p>The server answers with {@code welcome}, {@code state}, {@code closed}, {@code pong},
 * or {@code error} messages. {@code state} carries the same {@code room} snapshot the REST
 * endpoint returns, broadcast to every attached socket after any change.</p>
 */
@ServerEndpoint(LobbyEndpoint.PATH)
public class LobbyEndpoint {

    /** Path within this Element's WebSocket root → {@code /collection-ws/lobby}. */
    public static final String PATH = "/lobby";

    private static final Logger logger = LoggerFactory.getLogger(LobbyEndpoint.class);

    @OnOpen
    public void onOpen(final Session session) {
        logger.debug("lobby socket open: {}", session.getId());
    }

    @OnMessage
    public void onMessage(final Session session, final String message) {
        try {
            final JsonNode node = Wire.tree(message);
            final String type = text(node, "type");
            if (type == null) {
                error(session, "missing message type");
                return;
            }
            switch (type) {
                case "host" -> lobby().hostConnect(text(node, "code"), text(node, "token"), session);
                case "join" -> lobby().join(text(node, "code"), text(node, "name"), text(node, "playerId"), session);
                case "start" -> lobby().startGame(session, text(node, "gameId"), text(node, "episodeId"));
                case "settings" -> lobby().updateSettings(session, settings(node));
                case "choose-work" -> lobby().chooseWork(session, text(node, "workId"));
                case "submit-answer" -> lobby().submitAnswer(session, text(node, "text"));
                case "cast-vote" -> lobby().castVote(
                        session, text(node, "answerId"), node.path("golden").asBoolean(false));
                case "advance" -> lobby().advance(session);
                case "leave" -> lobby().closeRoom(session);
                case "ping" -> Wire.send(session, Wire.object().put("type", "pong"));
                default -> error(session, "unknown message type: " + type);
            }
        } catch (final LobbyException e) {
            error(session, e.getMessage());
        } catch (final Exception e) {
            logger.warn("invalid lobby message from {}: {}", session.getId(), message, e);
            error(session, "invalid message");
        }
    }

    @OnClose
    public void onClose(final Session session, final CloseReason reason) {
        try {
            lobby().disconnect(session);
        } catch (final Exception e) {            logger.debug("disconnect cleanup failed for {}: {}", session.getId(), e.getMessage());
        }
    }

    @OnError
    public void onError(final Session session, final Throwable error) {
        logger.debug("lobby socket error for {}: {}", session.getId(), error.getMessage());
    }

    private LobbyService lobby() {
        return ElementSupplier.getElementLocal(LobbyEndpoint.class).get()
                .getServiceLocator().getInstance(LobbyService.class);
    }

    private static LobbySettings settings(final JsonNode node) {
        final JsonNode settings = node.get("settings");
        if (settings == null || settings.isNull()) {
            return null;
        }
        final boolean familyFriendly = !settings.has("familyFriendly") || settings.get("familyFriendly").asBoolean(true);
        final int timer = settings.has("answerTimerSeconds") ? settings.get("answerTimerSeconds").asInt(30) : 30;
        final int rounds = settings.has("rounds") ? settings.get("rounds").asInt(3) : 3;
        return new LobbySettings(familyFriendly, timer, rounds);
    }

    private static String text(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static void error(final Session session, final String message) {
        Wire.send(session, Wire.object().put("type", "error").put("message", message));
    }

}
