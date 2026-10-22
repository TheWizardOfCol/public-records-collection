package com.publicrecords.collection.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.publicrecords.collection.lobby.CreatedRoom;
import com.publicrecords.collection.lobby.LobbyRegistry;
import com.publicrecords.collection.lobby.LobbySettings;
import com.publicrecords.collection.lobby.RoomView;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;
import dev.getelements.elements.sdk.annotation.ElementServiceImplementation;
import jakarta.websocket.Session;

import java.util.Optional;

/**
 * Thin exported facade over {@link LobbyRegistry}. All room state and realtime fan-out
 * live in the registry; this exists so the REST resource and the lobby WebSocket can
 * reach the registry through the service locator, which only resolves exported services.
 */
@Singleton
@ElementServiceImplementation
@ElementServiceExport(LobbyService.class)
public class LobbyServiceImpl implements LobbyService {

    private final LobbyRegistry registry;

    @Inject
    public LobbyServiceImpl(final LobbyRegistry registry) {
        this.registry = registry;
    }

    @Override
    public CreatedRoom createRoom(final String hostName) {
        return registry.createRoom(hostName);
    }

    @Override
    public Optional<RoomView> getRoom(final String code) {
        return registry.getRoom(code);
    }

    @Override
    public void hostConnect(final String code, final String token, final Session session) {
        registry.hostConnect(code, token, session);
    }

    @Override
    public void join(final String code, final String name, final String playerId, final Session session) {
        registry.join(code, name, playerId, session);
    }

    @Override
    public void startGame(final Session session, final String gameId, final String episodeId) {
        registry.startGame(session, gameId, episodeId);
    }

    @Override
    public void updateSettings(final Session session, final LobbySettings settings) {
        registry.updateSettings(session, settings);
    }

    @Override
    public void closeRoom(final Session session) {
        registry.closeRoom(session);
    }

    @Override
    public void disconnect(final Session session) {
        registry.disconnect(session);
    }

    @Override
    public void chooseWork(final Session session, final String workId) {
        registry.chooseWork(session, workId);
    }

    @Override
    public void submitAnswer(final Session session, final String text) {
        registry.submitAnswer(session, text);
    }

    @Override
    public void castVote(final Session session, final String answerId, final boolean golden) {
        registry.castVote(session, answerId, golden);
    }

    @Override
    public void advance(final Session session) {
        registry.advance(session);
    }

}
