package com.publicrecords.collection.cryptid;

import com.publicrecords.collection.getlit.ScoreEntry;
import com.publicrecords.collection.lobby.LobbyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(CryptidCinemaGame.class);

    public static final String PHASE_BRIEF = "brief";
    public static final String PHASE_RECORD = "record";
    public static final String PHASE_VOTE = "vote";
    public static final String PHASE_RESULTS = "results";
    public static final String PHASE_FINISHED = "finished";
    public static final String PHASE_FINALE = "finale";
    /** Seconds between layered winners that share one window. */
    private static final double LAYER_STAGGER = 0.6;

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

    /** The screened winners per finished round (round index -> submissions, vote order). */
    private final Map<Integer, List<Submission>> screenedWinners = new HashMap<>();
    /** Winning song of the session (foundtrack choice id, when the episode has one). */
    private String winningSongId;

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
                    // Small groups skip the vote; the round is scored either way
                    // so the grand screening has its winners.
                    scoreRound();
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
                        phase = PHASE_FINALE;
                    }
                }
                case PHASE_FINALE -> phase = PHASE_FINISHED;
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
            if ("same".equals(round.promptMode())) {
                // "Make the sound of..." — every player gets the same one.
                for (final String id : playerIds) {
                    playerPrompts.put(id, pool.get(0));
                }
            } else {
                final List<String> shuffled = new ArrayList<>(pool);
                java.util.Collections.shuffle(shuffled, random);
                for (int i = 0; i < playerIds.size(); i++) {
                    playerPrompts.put(playerIds.get(i), shuffled.get(i % shuffled.size()));
                }
            }
        }

        // The fixed-hand choice rounds (FOUNDTRACK songs, UFSFX sound effects) are
        // dealt like a hand of cards: every player gets their own five picks drawn
        // at random from the full deck, rather than one shared set.
        if (round.deck() != null && !round.deck().isEmpty()
                && (round.type() == RoundType.FOUNDTRACK || round.type() == RoundType.UFSFX)) {
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
        logger.info("[score] scoring round {} ({} submissions)", roundIndex + 1, playerToSubmission.size());
        for (final String id : playerIds) {
            roundScores.put(id, 0);
        }
        normalVotes.values().forEach(submissionId -> award(submissionId, VOTE_POINTS));
        props.values().forEach(submissionId -> award(submissionId, PROP_POINTS));
        roundScores.forEach((id, points) -> totals.merge(id, points, Integer::sum));
        // Remember this round's screened winners for the grand screening — the
        // Unknowable rounds layer their top three moments apart.
        final int roundNo = roundIndex;
        final List<Submission> ranked = new ArrayList<>();
        for (final String id : playerIds) {
            final String sid = playerToSubmission.get(id);
            if (sid != null) {
                ranked.add(submissionById.get(sid));
            }
        }
        ranked.sort((a, b) -> Integer.compare(pointsFor(b.id), pointsFor(a.id)));
        final int cap = currentRound().type() == RoundType.UNKNOWABLE_SFX ? 3 : 1;
        screenedWinners.put(roundNo, new ArrayList<>(ranked.subList(0, Math.min(cap, ranked.size()))));
        if (currentRound().type() == RoundType.FOUNDTRACK && !ranked.isEmpty()) {
            winningSongId = ranked.get(0).choiceId;
        }
    }

    /** The grand screening's schedule, in episode order, winners layered per window. */
    private List<FinaleSegmentView> finaleSchedule() {
        logger.info("[finale] screened rounds: {}", screenedWinners.keySet());
        for (final Map.Entry<Integer, List<Submission>> e : screenedWinners.entrySet()) {
            logger.info("[finale] round {} winners: {}", e.getKey() + 1,
                    e.getValue().stream().map(w -> w.playerId).toList());
        }
        logger.info("[finale] song: {}", winningSongId);
        final List<FinaleSegmentView> schedule = new ArrayList<>();
        for (int i = 0; i < episode.rounds().size(); i++) {
            final Round round = episode.rounds().get(i);
            final List<Submission> winners = screenedWinners.getOrDefault(i, List.of());
            if (round.type() == RoundType.FOUNDTRACK) {
                final String url = winningSongId == null ? null : episode.songUrl(winningSongId);
                schedule.add(new FinaleSegmentView(
                        i + 1, "song", 0.0, 0.0, 0.0, List.of(), url,
                        winners.isEmpty() ? null : names.get(winners.get(0).playerId)));
                continue;
            }
            if (winners.isEmpty()) {
                continue;
            }
            final double start = Math.max(round.recordStart(), 0.0);
            final double end = Math.max(round.recordEnd(), start);
            final List<CryptidSubmissionView> winnerViews = new ArrayList<>();
            for (final Submission w : winners) {
                winnerViews.add(submissionView(w, round));
            }
            schedule.add(new FinaleSegmentView(
                    i + 1,
                    "audio",
                    start,
                    end,
                    winners.size() > 1 ? LAYER_STAGGER : 0.0,
                    winnerViews,
                    submissionAudioUrl(winners.get(0), round),
                    names.get(winners.get(0).playerId)));
        }
        return schedule;
    }

    private CryptidSubmissionView submissionView(final Submission s, final Round round) {
        return new CryptidSubmissionView(
                s.id,
                s.playerId,
                names.get(s.playerId),
                s.kind,
                s.label,
                s.choiceId,
                s.recordingId,
                submissionAudioUrl(s, round),
                pointsFor(s.id));
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

            final boolean finale = phase.equals(PHASE_FINALE);
            final List<FinaleSegmentView> schedule = finale ? finaleSchedule() : List.of();
            return new CryptidPublicView(
                    phase,
                    episode.id(),
                    episode.title(),
                    episode.blurb(),
                    finale ? episode.videoNoMusic() : episode.video(),
                    episode.videoNoMusic(),
                    roundIndex + 1,
                    episode.rounds().size(),
                    round.type().name(),
                    round.seeStart(),
                    round.seeEnd(),
                    round.recordStart(),
                    round.recordEnd(),
                    round.soundCutoff(),
                    round.screenMode(),
                    round.promptMode(),
                    round.directorNotes(),
                    round.prompts(),
                    publicChoices(round),
                    submitted,
                    playerIds.size(),
                    submissions,
                    roundEntries,
                    sortedScores(totals),
                    schedule);
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
        if (round.type() != RoundType.FOUNDTRACK && round.type() != RoundType.UFSFX) {
            return round.choices();
        }
        // Only the foundtrack songs preview on-device; the U.F.S.F.X. effects are
        final boolean previews = round.type() == RoundType.FOUNDTRACK || round.type() == RoundType.UFSFX;
        return playerHands.getOrDefault(playerId, List.of()).stream()
                .map(c -> new Choice(
                        c.id(),
                        c.label(),
                        previews
                                ? (c.audioUrl() != null ? c.audioUrl() : previewUrl(c.id()))
                                : null))
                .toList();
    }

    /**
     * The options as the host screen sees them. Foundtrack hands are private per player, so
     * the host gets an empty list (it keys the winning song off each submission's
     * {@code choiceId} instead); other choice rounds are passed through unchanged.
     */
    private List<Choice> publicChoices(final Round round) {
        return round.type() == RoundType.FOUNDTRACK || round.type() == RoundType.UFSFX
                ? List.of()
                : round.choices();
    }

    /**
     * Where a submission's audio lives: the uploaded recording, a foundtrack song's preview,
     * or nothing (a non-foundtrack choice has no audio of its own).
     */
    private String submissionAudioUrl(final Submission s, final Round round) {
        if (s.recordingId != null) {
            return "/collection/recordings/" + s.recordingId;
        }
        if (s.choiceId != null
                && (round.type() == RoundType.FOUNDTRACK || round.type() == RoundType.UFSFX)) {
            return choiceAudioUrl(round, s.choiceId);
        }
        return null;
    }

    private static String previewUrl(final String choiceId) {
        return FOUNDTRACK_MUSIC_BASE + choiceId + ".mp3";
    }

    /** A chosen option's audio: the deck entry's own URL, else the music-dir convention. */
    private static String choiceAudioUrl(final Round round, final String choiceId) {
        return round.deck().stream()
                .filter(c -> c.id().equals(choiceId))
                .map(Choice::audioUrl)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(previewUrl(choiceId));
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
