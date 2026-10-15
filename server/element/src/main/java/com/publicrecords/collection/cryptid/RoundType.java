package com.publicrecords.collection.cryptid;

/**
 * The kinds of rounds a Cryptid Cinema episode is built from. See the producer's episode
 * breakdown for the full definitions; in short:
 *
 * <ul>
 *   <li>{@link #UNDYINGALOG} — players record dialogue over a muted region of the film.</li>
 *   <li>{@link #FOLEY} — players record a sound effect for a region they can see.</li>
 *   <li>{@link #UNKNOWABLE_SFX} — players record from a prompt without seeing the clip.</li>
 *   <li>{@link #UFSFX} — players pick a pre-recorded sound effect from a hand.</li>
 *   <li>{@link #FOUNDTRACK} — players pick the song used for the screening.</li>
 * </ul>
 */
public enum RoundType {

    UNDYINGALOG,
    FOLEY,
    UNKNOWABLE_SFX,
    UFSFX,
    FOUNDTRACK;

    /** True when the round asks players to record their own audio. */
    public boolean isRecording() {
        return this == UNDYINGALOG || this == FOLEY || this == UNKNOWABLE_SFX;
    }

    /** True when the round asks players to pick from a supplied library (sounds or songs). */
    public boolean isChoice() {
        return this == UFSFX || this == FOUNDTRACK;
    }

}
