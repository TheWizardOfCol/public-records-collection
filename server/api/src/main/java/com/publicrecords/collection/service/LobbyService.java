package com.publicrecords.collection.service;

import com.publicrecords.collection.lobby.CreatedRoom;
import com.publicrecords.collection.lobby.LobbySettings;
import com.publicrecords.collection.lobby.RoomView;
import jakarta.websocket.Session;

import java.util.Optional;

/**
 * Exported lobby surface for The Public Records Collection Element.
 *
 * <p>This is the single access point both the REST resource and the lobby WebSocket
 * use: the REST side opens a room and reads a snapshot, while the WebSocket side drives
 * the live room (host claim, joins, start, settings, close, disconnect) on behalf of
 * connected sockets. It is implemented over the Element's in-memory
 * {@code LobbyRegistry}.</p>
 *
 * <p>Resolved via the Elements service locator (JAX-RS resources and WebSocket
 * endpoints are container-instantiated, so they cannot {@code @Inject} it).</p>
 */
public interface LobbyService {

    /**
     * Opens a new, empty room owned by the named host.
     *
     * @param hostName display name for the host connection (defaulted when blank)
     * @return the room code and the secret host token
     */
    CreatedRoom createRoom(String hostName);

    /**
     * @param code a room code, case-insensitive
     * @return a snapshot of the room, or empty if no such room exists
     */
    Optional<RoomView> getRoom(String code);

    /**
     * Attaches the host's socket, authorizing it with the room's secret host token.
     *
     * @throws com.publicrecords.collection.lobby.LobbyException if the room or token is not valid
     */
    void hostConnect(String code, String token, Session session);

    /**
     * Adds a player to the room behind {@code code} and welcomes them.
     *
     * @throws com.publicrecords.collection.lobby.LobbyException if the room is missing or the name is blank
     */
    void join(String code, String name, String playerId, Session session);

    /**
     * Host-only: records the chosen game and moves the room into its starting phase.
     */
    void startGame(Session session, String gameId);

    /**
     * Host-only: replaces the lobby settings for the sender's room.
     */
    void updateSettings(Session session, LobbySettings settings);

    /**
     * Host-only: tears the room down and tells every attached client.
     */
    void closeRoom(Session session);

    /**
     * Cleans up after a socket drops: forgets the host/player and broadcasts the new
     * state, discarding the room once nothing is left attached.
     */
    void disconnect(Session session);

    /**
     * Player action: choose the work to answer from during a game's work-selection phase.
     *
     * @throws com.publicrecords.collection.lobby.LobbyException if the game/phase/work is not valid
     */
    void chooseWork(Session session, String workId);

    /**
     * Player action: submit a passage from the player's own work for the current prompt.
     *
     * @throws com.publicrecords.collection.lobby.LobbyException if the passage is invalid
     */
    void submitAnswer(Session session, String text);

    /**
     * Player action: spend a normal or golden heart on another player's answer.
     *
     * @throws com.publicrecords.collection.lobby.LobbyException if the vote is not allowed
     */
    void castVote(Session session, String answerId, boolean golden);

    /**
     * Host-only: advance the current game one phase (select → answer → vote → results → …).
     */
    void advance(Session session);

}
