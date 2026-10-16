package com.publicrecords.collection.lobby;

import com.publicrecords.collection.getlit.GetLitContent;
import com.publicrecords.collection.cryptid.CryptidCinemaGame;
import com.publicrecords.collection.cryptid.CryptidCinemaContent;
import com.publicrecords.collection.getlit.GetLitGame;
import jakarta.websocket.Session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Mutable, in-memory state for a single lobby room: the host connection, the players
 * currently attached, the lobby settings, and the lifecycle phase.
 *
 * <p>Transport-agnostic on purpose — it knows about {@link Session}s only so
 * {@link LobbyRegistry} can broadcast. Every accessor runs under {@link #lock} because
 * joins, leaves, and host actions arrive concurrently from different sockets.</p>
 */
final class Room {

    /** Reserved id for the host connection in the players list. */
    static final String HOST_ID = "host";

    private final Object lock = new Object();

    private final String code;
    private final String hostToken;
    private final String hostName;

    private final Map<String, Player> players = new LinkedHashMap<>();

    private Session hostSession;
    private LobbySettings settings = LobbySettings.defaults();
    private String gameId;
    private String phase = "lobby";
    private Object game;

    Room(final String code, final String hostToken, final String hostName) {
        this.code = code;
        this.hostToken = hostToken;
        this.hostName = hostName;
    }

    String code() {
        return code;
    }

    String hostToken() {
        return hostToken;
    }

    void attachHost(final Session session) {
        synchronized (lock) {
            hostSession = session;
        }
    }

    /**
     * Adds a player, or replaces an existing one if a known id is supplied (so a
     * client may reclaim its seat and change its name).
     */
    PlayerView attachPlayer(final String requestedId, final String name, final Session session) {
        synchronized (lock) {
            final String id = (requestedId != null && !requestedId.isBlank())
                    ? requestedId
                    : UUID.randomUUID().toString();
            final Player player = new Player(id, name, session);
            players.put(id, player);
            return view(player);
        }
    }

    void detach(final Session session, final String playerId, final boolean host) {
        synchronized (lock) {
            if (host && session == hostSession) {
                hostSession = null;
            }
            if (playerId != null) {
                final Player player = players.get(playerId);
                if (player != null && player.session == session) {
                    players.remove(playerId);
                }
            }
        }
    }

    /**
     * Starts a game, snapshotting the current players into the authoritative game object
     * for {@code gameId} — Get Lit or Cryptid Cinema.
     */
    void startGame(final String gameId, final String episodeId, final GetLitContent content, final CryptidCinemaContent ccContent) {
        synchronized (lock) {
            if (players.isEmpty()) {
                throw new LobbyException("at least one player is required to start");
            }
            this.gameId = gameId;
            if ("get-lit".equals(gameId)) {
                this.game = new GetLitGame(
                        content,
                        new ArrayList<>(players.keySet()),
                        namesLocked(),
                        settings.familyFriendly(),
                        settings.rounds(),
                        new Random());
                this.phase = "in-progress";
            } else if ("cryptid-cinema".equals(gameId)) {
                final var episode = (episodeId != null && !episodeId.isBlank())
                        ? ccContent.episode(episodeId)
                                .orElseThrow(() -> new LobbyException("no such episode: " + episodeId))
                        : ccContent.first()
                                .orElseThrow(() -> new LobbyException("no Cryptid Cinema episodes are available"));
                this.game = new CryptidCinemaGame(
                        episode,
                        new ArrayList<>(players.keySet()),
                        namesLocked(),
                        new Random());
                this.phase = "in-progress";
            } else {
                throw new LobbyException("unknown game: " + gameId);
            }
        }
    }

    Object game() {
        synchronized (lock) {
            return game;
        }
    }

    private Map<String, String> namesLocked() {
        final Map<String, String> names = new LinkedHashMap<>();
        for (final Player player : players.values()) {
            names.put(player.id, player.name);
        }
        return names;
    }

    void updateSettings(final LobbySettings settings) {
        synchronized (lock) {
            this.settings = settings;
        }
    }

    boolean isEmpty() {
        synchronized (lock) {
            return hostSession == null && players.isEmpty();
        }
    }

    Collection<Session> sessions() {
        synchronized (lock) {
            final var sessions = new ArrayList<Session>(players.size() + 1);
            if (hostSession != null) {
                sessions.add(hostSession);
            }
            for (final Player player : players.values()) {
                sessions.add(player.session);
            }
            return sessions;
        }
    }

    RoomView snapshot() {
        synchronized (lock) {
            final var list = new ArrayList<PlayerView>(players.size() + 1);
            list.add(new PlayerView(HOST_ID, hostName, true, hostSession != null));
            for (final Player player : players.values()) {
                list.add(view(player));
            }
            return new RoomView(code, phase, gameId, settings, list);
        }
    }

    private static PlayerView view(final Player player) {
        return new PlayerView(player.id, player.name, false, true);
    }

    private static final class Player {

        final String id;
        final String name;
        final Session session;

        Player(final String id, final String name, final Session session) {
            this.id = id;
            this.name = name;
            this.session = session;
        }

    }

}
