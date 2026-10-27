package com.publicrecords.collection.cryptid;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A Cryptid Cinema episode: its identity, its film clips (with and without the original
 * music), and the ordered list of {@link Round}s that make it up.
 */
public record Episode(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("blurb") String blurb,
        @JsonProperty("video") String video,
        @JsonProperty("videoNoMusic") String videoNoMusic,
        @JsonProperty("hasFoundtrack") boolean hasFoundtrack,
        @JsonProperty("rounds") List<Round> rounds) {

    public Episode {
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
    }

    public int roundCount() {
        return rounds.size();
    }

    /** Where a foundtrack song's mp3 lives (the deck ids map to the music dir). */
    public String songUrl(final String choiceId) {
        if (choiceId == null) {
            return null;
        }
        for (final Round round : rounds) {
            for (final Choice choice : round.deck()) {
                if (choiceId.equals(choice.id()) && choice.audioUrl() != null && !choice.audioUrl().isBlank()) {
                    return choice.audioUrl();
                }
            }
        }
        return "/app/static/collection/assets/cryptid/music/" + choiceId + ".mp3";
    }

}
