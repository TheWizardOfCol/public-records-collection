package com.publicrecords.collection.cryptid;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CryptidCinemaGame {
  private final List<String> playerIds;
  private final Map<String, String> playerNames;
  private final String phase = "select"; // minimal stub

  public CryptidCinemaGame(List<String> playerIds, Map<String, String> playerNames) {
    this.playerIds = new ArrayList<>(playerIds);
    this.playerNames = new HashMap<>(playerNames);
  }

  public PublicView publicView() {
    return new PublicView(phase, playerIds.size());
  }

  public PrivateView privateView(String playerId) {
    return new PrivateView();
  }

  public void chooseWork(String playerId, String workId) {}
  public void submitAnswer(String playerId, String text) {}
  public void castVote(String playerId, String answerId, boolean golden) {}
  public void advance() {}

  public static class PublicView {
    @JsonProperty public final String phase;
    @JsonProperty public final int players;
    public PublicView(String phase, int players) { this.phase=phase; this.players=players; }
  }
  public static class PrivateView {}
}
