package com.publicrecords.collection.cryptid;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One round of a Cryptid Cinema episode: what part of the film the players see, what part
 * they record over (or where the chosen sound lands), and the prompt/choice material.
 *
 * <p>Timings are seconds from the top of the episode's film clip. {@code maxSeconds} caps a
 * recording when the round needs it (0 = use the marked region's own length). {@code choices}
 * is populated for choice rounds (UFSFX/FOUNDTRACK) and empty otherwise.</p>
 */
public record Round(
        @JsonProperty("index") int index,
        @JsonProperty("type") RoundType type,
        @JsonProperty("seeStart") double seeStart,
        @JsonProperty("seeEnd") double seeEnd,
        @JsonProperty("recordStart") double recordStart,
        @JsonProperty("recordEnd") double recordEnd,
        @JsonProperty("maxSeconds") double maxSeconds,
        @JsonProperty("directorNotes") String directorNotes,
        @JsonProperty("prompts") List<String> prompts,
        @JsonProperty("choices") List<Choice> choices) {

    public Round {
        prompts = prompts == null ? List.of() : List.copyOf(prompts);
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    /** Length of the marked recording region, in seconds. */
    public double windowSeconds() {
        return Math.max(0.0, recordEnd - recordStart);
    }

    /** The length a recording should be: {@code maxSeconds} when set, else the marked region. */
    public double recordSeconds() {
        return maxSeconds > 0.0 ? maxSeconds : windowSeconds();
    }

    public boolean isRecording() {
        return type.isRecording();
    }

    public boolean isChoice() {
        return type.isChoice();
    }

}
