package com.publicrecords.collection.cryptid;

import com.publicrecords.collection.getlit.ScoreEntry;
import com.publicrecords.collection.lobby.LobbyException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The authoritative Cryptid Cinema game — drives one room through an episode's rounds.
 *
 * <p>Rules implemented (see the producer's episode breakdown): for each round the host
 * shows the players a look at the film and a director's note; every player then records a
 * take (or picks an option, for choice rounds) and submits it. With three or more players,
 * everyone then votes — a vote is {@value #VOTE_POINTS} points and a "prop" is
 * {@value #PROP_POINTS} — and the points score the submission's author. With one or two
 * players there is no voting; their takes are used directly. All state lives here; the
 * transport layer only relays the two views.</p>
 *
 * <p>Every public method is guarded by {@link #lock}, since actions for different players
 * arrive concurrently from separate WebSocket threads.</p>
 */
public class CryptidCinemaGame {

    public static final String PHASE_BRIEF = "brief";
    public static final String PHASE_RECORD = "record";
    public static final String PHASE_VOTE = "vote";
    public static final String PHASE_RESULTS = "results";
    public static final String PHASE_FINISHED = "finished";

    private static final int VOTE_POINTS = 100;
    private static final int PROP_POINTS = 10;

    private static final int VOTING_THRESHOLD = 3;

    /** How many songs from the foundtrack deck each player is dealt. */
    private static final int FOUNDTRACK_HAND_SIZE = 5;

    /** Static root the foundtrack song previews are served from (see the element's pom). */
    private static final String FOUNDTRACK_MUSIC_BASE = "/app/static/collection/assets/cryptid/music/";

    private final Object lock = new Object();

    private final Episode episode;
    private final List<String> playerIds;
    private final Map<String, String> names;
    private final Random random;

    private String phase = PHASE_BRIEF;
    private int roundIndex = 0;

    private final Map<String, String> playerPrompts = new HashMap<>();
    private final Map<String, List<Choice>> playerHands = new HashMap<>();
    private final Map<String, Submission> submissionById = new LinkedHashMap<>();
    private final Map<String, String> playerToSubmission = new HashMap<>();
    private final Map<String, String> normalVotes = new HashMap<>();
    private final Map<String, String> props = new HashMap<>();

    private final Map<String, Integer> totals = new HashMap<>();
    private final Map<String, Integer> roundScores = new HashMap<>();

    public CryptidCinemaGame(
            final Episode episode,
            final List<String> playerIds,
            final Map<String, String> names,
            final Random random) {
        this.episode = episode;
        this.playerIds = List.copyOf(playerIds);
        this.names = Map.copyOf(names);
        this.random = random;
        for (final String id : this.playerIds) {
            totals.put(id, 0);
        }
        prepareRound();
    }

    // --- actions --------------------------------------------------------------

    /** No-op: Cryptid Cinema has no work-selection phase (kept for transport parity). */
    public void chooseWork(final String playerId, final String workId) {
        // intentionally empty
    }

    /**
     * A player's contribution to the current round: an uploaded recording id for a recording
     * round, or a choice id for a choice round.
     */
    public void submitAnswer(final String playerId, final String value) {
        synchronized (lock) {
            requirePhase(PHASE_RECORD);
            requirePlayer(playerId);
            if (playerToSubmission.containsKey(playerId)) {
                throw new LobbyException("you already submitted this round");
            }
            final Round round = currentRound();
            final String payload = value == null ? "" : value.trim();
            if (payload.isEmpty()) {
                throw new LobbyException("nothing was submitted");
            }

            final Submission submission;
            if (round.isChoice()) {
                final Choice choice = choicesFor(playerId, round).stream()
                        .filter(c -> c.id().equals(payload))
                        .findFirst()
                        .orElseThrow(() -> new LobbyException("no such option"));
                submission = new Submission(
                        UUID.randomUUID().toString(), playerId, "choice", choice.label(), choice.id(), null);
            } else {
                submission = new Submission(
                        UUID.randomUUID().toString(), playerId, "recording", null, null, payload);
            }
            submissionById.put(submission.id, submission);
            playerToSubmission.put(playerId, submission.id);
        }
    }

    /**
     * A vote ({@code prop == false}, {@value #VOTE_POINTS} points) or a prop
     * ({@code prop == true}, {@value #PROP_POINTS} points) on someone else's submission.
     */
    public void castVote(final String playerId, final String submissionId, final boolean prop) {
        synchronized (lock) {
            requirePhase(PHASE_VOTE);
            requirePlayer(playerId);
            final Submission submission = submissionById.get(submissionId);
            if (submission == null) {
                throw new LobbyException("no such submission");
            }
            if (submission.playerId.equals(playerId)) {
                throw new LobbyException("you cannot vote for your own take");
            }
            if (prop) {
                if (props.containsKey(playerId)) {
                    throw new LobbyException("you already used your prop");
                }
                props.put(playerId, submissionId);
            } else {
                if (normalVotes.containsKey(playerId)) {
                    throw new LobbyException("you already voted");
                }
                normalVotes.put(playerId, submissionId);
            }
        }
    }

    /** Host-only: advance the state machine one step. */
    public void advance() {
        synchronized (lock) {
            switch (phase) {
                case PHASE_BRIEF -> phase = PHASE_RECORD;
                case PHASE_RECORD -> {
                    requireEveryoneSubmitted();
                    phase = (playerIds.size() >= VOTING_THRESHOLD) ? PHASE_VOTE : PHASE_RESULTS;
                }
                case PHASE_VOTE -> {
                    scoreRound();
                    phase = PHASE_RESULTS;
                }
                case PHASE_RESULTS -> {
                    if (roundIndex + 1 < episode.rounds().size()) {
                        roundIndex++;
                        prepareRound();
                        phase = PHASE_BRIEF;
                    } else {
                        phase = PHASE_FINISHED;
                    }
                }
                case PHASE_FINISHED -> {
                    // nothing left to advance
                }
                default -> throw new LobbyException("cannot advance from " + phase);
            }
        }
    }

    private void prepareRound() {
        playerPrompts.clear();
        playerHands.clear();
        submissionById.clear();
        playerToSubmission.clear();
        normalVotes.clear();
        props.clear();
        roundScores.clear();

        final Round round = currentRound();

        final List<String> pool = round.prompts();
        if (!pool.isEmpty()) {
            final List<String> shuffled = new ArrayList<>(pool);
            java.util.Collections.shuffle(shuffled, random);
            for (int i = 0; i < playerIds.size(); i++) {
                playerPrompts.put(playerIds.get(i), shuffled.get(i % shuffled.size()));
            }
        }

        // Foundtrack is dealt like a hand of cards: every player gets their own
        // five songs drawn at random from the full deck, rather than one shared set.
        if (round.type() == RoundType.FOUNDTRACK && !round.deck().isEmpty()) {
            for (final String id : playerIds) {
                final List<Choice> shuffled = new ArrayList<>(round.deck());
                java.util.Collections.shuffle(shuffled, random);
                playerHands.put(id, List.copyOf(shuffled.subList(0, Math.min(FOUNDTRACK_HAND_SIZE, shuffled.size()))));
            }
        }
    }

    private void requireEveryoneSubmitted() {
        for (final String id : playerIds) {
            if (!playerToSubmission.containsKey(id)) {
                throw new LobbyException("not everyone has submitted yet");
            }
        }
    }

    private void scoreRound() {
        for (final String id : playerIds) {
            roundScores.put(id, 0);
        }
        normalVotes.values().forEach(submissionId -> award(submissionId, VOTE_POINTS));
        props.values().forEach(submissionId -> award(submissionId, PROP_POINTS));
        roundScores.forEach((id, points) -> totals.merge(id, points, Integer::sum));
    }

    private void award(final String submissionId, final int points) {
        final Submission submission = submissionById.get(submissionId);
        if (submission != null) {
            roundScores.merge(submission.playerId, points, Integer::sum);
        }
    }

    // --- views ----------------------------------------------------------------

    public CryptidPublicView publicView() {
        synchronized (lock) {
            final Round round = currentRound();
            final boolean reveal = phase.equals(PHASE_RESULTS) || phase.equals(PHASE_FINISHED);
            final boolean showSubmissions = phase.equals(PHASE_VOTE) || reveal;

            final List<CryptidSubmissionView> submissions = new ArrayList<>();
            if (showSubmissions) {
                for (final String id : playerIds) {
                    final String submissionId = playerToSubmission.get(id);
                    if (submissionId == null) {
                        continue;
                    }
                    final Submission s = submissionById.get(submissionId);
                    submissions.add(new CryptidSubmissionView(
                            s.id,
                            s.playerId,
                            reveal ? names.get(s.playerId) : null,
                            s.kind,
                            s.label,
                            s.choiceId,
                            s.recordingId,
                            submissionAudioUrl(s, round),
                            reveal ? pointsFor(s.id) : null));
                }
            }

            final int submitted = (int) playerIds.stream().filter(playerToSubmission::containsKey).count();
            final List<ScoreEntry> roundEntries = reveal ? sortedScores(roundScores) : List.of();

            return new CryptidPublicView(
                    phase,
                    episode.id(),
                    episode.title(),
                    episode.blurb(),
                    episode.video(),
                    episode.videoNoMusic(),
                    roundIndex + 1,
                    episode.rounds().size(),
                    round.type().name(),
                    round.seeStart(),
                    round.seeEnd(),
                    round.recordStart(),
                    round.recordEnd(),
                    round.directorNotes(),
                    round.prompts(),
                    publicChoices(round),
                    submitted,
                    playerIds.size(),
                    submissions,
                    roundEntries,
                    sortedScores(totals));
        }
    }

    public CryptidPrivateView privateView(final String playerId) {
        synchronized (lock) {
            final Round round = currentRound();
            final String submissionId = playerToSubmission.get(playerId);
            final Submission submission = submissionId == null ? null : submissionById.get(submissionId);
            final boolean canSubmit = phase.equals(PHASE_RECORD) && submissionId == null;

            return new CryptidPrivateView(
                    phase,
                    roundIndex + 1,
                    episode.rounds().size(),
                    round.type().name(),
                    round.directorNotes(),
                    playerPrompts.get(playerId),
                    round.recordStart(),
                    round.recordEnd(),
                    round.recordSeconds(),
                    choicesFor(playerId, round),
                    submissionId,
                    submission == null ? null : submission.recordingId,
                    submission == null ? null : submission.label,
                    canSubmit,
                    roundScores.getOrDefault(playerId, 0),
                    totals.getOrDefault(playerId, 0));
        }
    }

    private int pointsFor(final String submissionId) {
        int points = 0;
        if (normalVotes.containsValue(submissionId)) {
            points += VOTE_POINTS;
        }
        if (props.containsValue(submissionId)) {
            points += PROP_POINTS;
        }
        return points;
    }

    private List<ScoreEntry> sortedScores(final Map<String, Integer> scores) {
        final List<ScoreEntry> entries = new ArrayList<>(playerIds.size());
        for (final String id : playerIds) {
            entries.add(new ScoreEntry(id, names.get(id), scores.getOrDefault(id, 0)));
        }
        entries.sort(Comparator.comparingInt(ScoreEntry::score).reversed());
        return entries;
    }

    private Round currentRound() {
        return episode.rounds().get(roundIndex);
    }

    /**
     * The options a single player may pick from: their dealt foundtrack hand (with the
     * preview URL attached, so a phone can listen before choosing), or the round's fixed
     * choice list for every other choice round.
     */
    private List<Choice> choicesFor(final String playerId, final Round round) {
        if (round.type() != RoundType.FOUNDTRACK) {
            return round.choices();
        }
        return playerHands.getOrDefault(playerId, List.of()).stream()
                .map(c -> new Choice(c.id(), c.label(), previewUrl(c.id())))
                .toList();
    }

    /**
     * The options as the host screen sees them. Foundtrack hands are private per player, so
     * the host gets an empty list (it keys the winning song off each submission's
     * {@code choiceId} instead); other choice rounds are passed through unchanged.
     */
    private List<Choice> publicChoices(final Round round) {
        return round.type() == RoundType.FOUNDTRACK ? List.of() : round.choices();
    }

    /**
     * Where a submission's audio lives: the uploaded recording, a foundtrack song's preview,
     * or nothing (a non-foundtrack choice has no audio of its own).
     */
    private String submissionAudioUrl(final Submission s, final Round round) {
        if (s.recordingId != null) {
            return "/collection/recordings/" + s.recordingId;
        }
        if (s.choiceId != null && round.type() == RoundType.FOUNDTRACK) {
            return previewUrl(s.choiceId);
        }
        return null;
    }

    private static String previewUrl(final String choiceId) {
        return FOUNDTRACK_MUSIC_BASE + choiceId + ".mp3";
    }

    private void requirePhase(final String expected) {
        if (!phase.equals(expected)) {
            throw new LobbyException("that action isn't available right now");
        }
    }

    private void requirePlayer(final String playerId) {
        if (playerId == null || !playerIds.contains(playerId)) {
            throw new LobbyException("you are not a player in this game");
        }
    }

    private static final class Submission {

        final String id;
        final String playerId;
        final String kind;
        final String label;
        final String choiceId;
        final String recordingId;

        Submission(final String id, final String playerId, final String kind,
                   final String label, final String choiceId, final String recordingId) {
            this.id = id;
            this.playerId = playerId;
            this.kind = kind;
            this.label = label;
            this.choiceId = choiceId;
            this.recordingId = recordingId;
        }

    }

}
