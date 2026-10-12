package com.publicrecords.collection.lobby;

/**
 * A recoverable lobby fault — bad room code, wrong host token, non-host attempting a
 * host action, malformed input. The WebSocket endpoint catches it and answers the
 * offending client with an {@code error} message rather than dropping the socket.
 */
public class LobbyException extends RuntimeException {

    public LobbyException(final String message) {
        super(message);
    }

}
