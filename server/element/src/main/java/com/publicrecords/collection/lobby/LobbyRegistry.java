package com.publicrecords.collection.lobby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.publicrecords.collection.getlit.GetLitContent;
import com.publicrecords.collection.cryptid.CryptidCinemaContent;
import com.publicrecords.collection.getlit.GetLitGame;
import com.publicrecords.collection.mallrats.MallRatsContent;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The lobby hub: owns every live room, mints room codes and host tokens, and fans room
 * state out to the connected sockets.
 *
 * <p>Rooms are in-memory and scoped to the running Elements process. That is
 * deliberate for the platform layer — a party pack's lobby is short-lived and single-
 * instance — and it's the one place to revisit if the game ever needs to survive a
 * server restart mid-session.</p>
 */
@Singleton
public class LobbyRegistry {

    /** Session user-property key holding the room the connection belongs to. */
    public static final String PROP_ROOM = "prc.lobby.room";

    /** Session user-property key holding the player id of the connection. */
    public static final String PROP_PLAYER = "prc.lobby.player";

    /** Session user-property key marking the connection as the host. */
    public static final String PROP_HOST = "prc.lobby.host";

    private static final Logger logger = LoggerFactory.getLogger(LobbyRegistry.class);

    /** Room-code alphabet with I/O/0/1 removed so codes are unambiguous when read aloud. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 4;

    private static final int MAX_NAME_LENGTH = 24;

    private final SecureRandom random = new SecureRandom();

    private final GetLitContent content;
    private final CryptidCinemaContent cryptidContent;
    private final MallRatsContent mallContent;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    @Inject
    public LobbyRegistry(final GetLitContent content, final CryptidCinemaContent cryptidContent, final MallRatsContent mallContent) {
        this.content = content;
        this.cryptidContent = cryptidContent;
        this.mallContent = mallContent;
    }

    /**
     * Opens a new room owned by {@code hostName}.
     *
     * @return the room code and the secret host token
     */
    public CreatedRoom createRoom(final String hostName) {
        final String name = normalizeName(hostName, "Host");
        for (int attempt = 0; attempt < 100; attempt++) {
            final String code = nextCode();
            final Room room = new Room(code, nextToken(), name);
            if (rooms.putIfAbsent(code, room) == null) {
                logger.info("opened lobby room {} for host '{}'", code, name);
                return new CreatedRoom(code, room.hostToken());
            }
        }
        throw new IllegalStateException("could not allocate a room code");
    }

    public Optional<RoomView> getRoom(final String code) {
        return Optional.ofNullable(rooms.get(normalizeCode(code))).map(Room::snapshot);
    }

    /**
     * Attaches the host's socket, authorizing it with the room's host token.
     */
    public void hostConnect(final String code, final String token, final Session session) {
        final Room room = require(code);
        if (token == null || !constantTimeEquals(token, room.hostToken())) {
            throw new LobbyException("invalid host token");
        }
        room.attachHost(session);
        session.getUserProperties().put(PROP_ROOM, room.code());
        session.getUserProperties().put(PROP_HOST, Boolean.TRUE);
        Wire.send(session, welcome(true, null, room.snapshot()));
        broadcastState(room);
    }

    /**
     * Adds a player to the room behind {@code code} and welcomes them.
     */
    public void join(final String code, final String rawName, final String playerId, final Session session) {
        final Room room = require(code);
        final String name = normalizeName(rawName, null);
        if (name == null) {
            throw new LobbyException("a name is required to join");
        }
        final PlayerView player = room.attachPlayer(playerId, name, session);
        session.getUserProperties().put(PROP_ROOM, room.code());
        session.getUserProperties().put(PROP_PLAYER, player.id());
        Wire.send(session, welcome(false, player.id(), room.snapshot()));
        broadcastState(room);
    }

    /**
     * Host-only: records the chosen game and moves the room into its {@code starting} phase.
     */
    public void startGame(final Session session, final String gameId, final String episodeId) {
        final Room room = requireHost(session);
        if (gameId == null || gameId.isBlank()) {
            throw new LobbyException("gameId is required");
        }
        room.startGame(gameId.trim(), episodeId, content, cryptidContent, mallContent);
        broadcastState(room);
    }

    /**
     * Player action: pick the work to answer from (Get Lit work-selection phase).
     */
    public void chooseWork(final Session session, final String workId) {
        final Room room = requireRoom(session);
        Object g = requireGame(room); if (g instanceof com.publicrecords.collection.getlit.GetLitGame gl) gl.chooseWork(playerOf(session),workId); if (g instanceof com.publicrecords.collection.cryptid.CryptidCinemaGame cc) cc.chooseWork(playerOf(session),workId);
        broadcastGame(room);
    }

    /**
     * Player action: submit a passage from their own work (Get Lit answering phase).
     */
    public void submitAnswer(final Session session, final String text) {
        final Room room = requireRoom(session);
        Object g = requireGame(room); if (g instanceof com.publicrecords.collection.getlit.GetLitGame gl) gl.submitAnswer(playerOf(session),text); if (g instanceof com.publicrecords.collection.cryptid.CryptidCinemaGame cc) cc.submitAnswer(playerOf(session),text);
        broadcastGame(room);
    }

    /**
     * Player action: star a Mall Rats pool gift (0-5).
     */
    public void rateGift(final Session session, final String giftId, final int stars) {
        final Room room = requireRoom(session);
        Object g = requireGame(room); if (g instanceof com.publicrecords.collection.mallrats.MallRatsGame mr) mr.rateGift(playerOf(session),giftId,stars);
        broadcastGame(room);
    }

    /**
     * Player action: assign a Mall Rats gift to another player.
     */
    public void shopGift(final Session session, final String recipientId, final String giftId) {
        final Room room = requireRoom(session);
        Object g = requireGame(room); if (g instanceof com.publicrecords.collection.mallrats.MallRatsGame mr) mr.shopGift(playerOf(session),recipientId,giftId);
        broadcastGame(room);
    }

    /**
     * Player action: spend a normal or golden heart on someone else's answer (Get Lit voting).
     */
    public void castVote(final Session session, final String answerId, final boolean golden) {
        final Room room = requireRoom(session);
        Object g = requireGame(room); if (g instanceof com.publicrecords.collection.getlit.GetLitGame gl) gl.castVote(playerOf(session),answerId,golden); if (g instanceof com.publicrecords.collection.cryptid.CryptidCinemaGame cc) cc.castVote(playerOf(session),answerId,golden);
        broadcastGame(room);
    }

    /**
     * Host action: advance the current game one phase.
     */
    public void advance(final Session session) {
        final Room room = requireHost(session);
        Object g = requireGame(room); if (g instanceof com.publicrecords.collection.getlit.GetLitGame gl) gl.advance(); if (g instanceof com.publicrecords.collection.cryptid.CryptidCinemaGame cc) cc.advance(); if (g instanceof com.publicrecords.collection.mallrats.MallRatsGame mr) mr.advance();
        broadcastState(room);
    }

    /**
     * Host-only: replaces the lobby settings.
     */
    public void updateSettings(final Session session, final LobbySettings settings) {
        final Room room = requireHost(session);
        if (settings == null) {
            throw new LobbyException("settings are required");
        }
        room.updateSettings(settings);
        broadcastState(room);
    }

    /**
     * Host-only: tears the room down and tells every client.
     */
    public void closeRoom(final Session session) {
        final Room room = requireHost(session);
        rooms.remove(room.code());
        logger.info("closed lobby room {}", room.code());
        final ObjectNode closed = Wire.object().put("type", "closed").put("reason", "host closed the room");
        for (final Session target : room.sessions()) {
            Wire.send(target, closed);
        }
    }

    /**
     * Cleans up after a socket drops: forget the host/player and broadcast the new state,
     * or discard the room once nothing is left attached.
     */
    public void disconnect(final Session session) {
        final Object code = session.getUserProperties().get(PROP_ROOM);
        if (code == null) {
            return;
        }
        final Room room = rooms.get(code.toString());
        if (room == null) {
            return;
        }
        final Object playerId = session.getUserProperties().get(PROP_PLAYER);
        final boolean host = Boolean.TRUE.equals(session.getUserProperties().get(PROP_HOST));
        room.detach(session, playerId == null ? null : playerId.toString(), host);
        if (room.isEmpty()) {
            rooms.remove(room.code());
            logger.info("discarded empty lobby room {}", room.code());
        } else {
            broadcastState(room);
        }
    }

    private Room require(final String code) {
        final Room room = rooms.get(normalizeCode(code));
        if (room == null) {
            throw new LobbyException("no such room");
        }
        return room;
    }

    private Room requireHost(final Session session) {
        final Object code = session.getUserProperties().get(PROP_ROOM);
        if (code == null || !Boolean.TRUE.equals(session.getUserProperties().get(PROP_HOST))) {
            throw new LobbyException("only the host can do that");
        }
        final Room room = rooms.get(code.toString());
        if (room == null) {
            throw new LobbyException("no such room");
        }
        return room;
    }

    private Room requireRoom(final Session session) {
        final Object code = session.getUserProperties().get(PROP_ROOM);
        if (code == null) {
            throw new LobbyException("you are not in a room");
        }
        final Room room = rooms.get(code.toString());
        if (room == null) {
            throw new LobbyException("no such room");
        }
        return room;
    }

    private static Object requireGame(final Room room) {
        final Object game = room.game();
        if (game == null) {
            throw new LobbyException("no game in progress");
        }
        return game;
    }

    private static String playerOf(final Session session) {
        final Object playerId = session.getUserProperties().get(PROP_PLAYER);
        if (playerId == null) {
            throw new LobbyException("you are not a player in this game");
        }
        return playerId.toString();
    }

    private void broadcastState(final Room room) {
        final ObjectNode message = Wire.object().put("type", "state");
        message.set("room", Wire.value(room.snapshot()));
        for (final Session session : room.sessions()) {
            Wire.send(session, message);
        }
        broadcastGame(room);
    }

    /**
     * Fans the active game out to every attached socket: the public view to the host, and the
     * public plus that player's private view to each player. The public view is computed once
     * and shared between frames.
     */
    private void broadcastGame(final Room room) {
        final Object game = room.game();
        if (game == null) {
            return;
        }
        final JsonNode publicView;
        final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            if (game instanceof com.publicrecords.collection.getlit.GetLitGame gl) {
                publicView = Wire.value(gl.publicView());
            } else if (game instanceof com.publicrecords.collection.cryptid.CryptidCinemaGame cc) {
                publicView = Wire.value(cc.publicView());
            } else if (game instanceof com.publicrecords.collection.mallrats.MallRatsGame mr) {
                publicView = Wire.value(mr.publicView());
            } else {
                return;
            }
        } catch (Exception e) { return; }
        for (final Session session : room.sessions()) {
            final ObjectNode message = Wire.object().put("type", "game");
            message.set("game", publicView);
            final Object playerId = session.getUserProperties().get(PROP_PLAYER);
            if (playerId != null) {
                String pid = playerId.toString();
                if (game instanceof com.publicrecords.collection.getlit.GetLitGame gl) {
                    message.set("you", Wire.value(gl.privateView(pid)));
                } else if (game instanceof com.publicrecords.collection.cryptid.CryptidCinemaGame cc) {
                    message.set("you", Wire.value(cc.privateView(pid)));
                } else if (game instanceof com.publicrecords.collection.mallrats.MallRatsGame mr) {
                    message.set("you", Wire.value(mr.privateView(pid)));
                }
            }
            Wire.send(session, message);
        }
    }

    private static ObjectNode welcome(final boolean host, final String playerId, final RoomView room) {
        final ObjectNode message = Wire.object().put("type", "welcome").put("host", host);
        if (playerId != null) {
            message.put("playerId", playerId);
        }
        message.set("room", Wire.value(room));
        return message;
    }

    private String nextCode() {
        final var builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    private String nextToken() {
        final byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        final var builder = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private static String normalizeCode(final String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private static String normalizeName(final String name, final String fallback) {
        if (name == null) {
            return fallback;
        }
        final String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    private static boolean constantTimeEquals(final String a, final String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

}
