package com.publicrecords.collection.getlit;

import com.publicrecords.collection.lobby.LobbyException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.publicrecords.collection.getlit.GetLitContent.normalize;

/**
 * The authoritative Get Lit game — a round-by-round state machine driving one room from work
 * selection through answering, voting, scoring, and a final winner.
 *
 * <p>Rules implemented (see the design doc §6.1): each player picks one work up front and
 * answers every prompt from it; each round a prompt is shown, players submit a passage from
 * their own work, all answers are revealed anonymously, players spend hearts, and the hearts
 * score the answer's author. A normal heart is 1 point, a golden heart is 3, and both may be
 * placed on someone else's answer only. Per round a player may give at most one normal heart
 * per answer and one golden heart in total; normal hearts are otherwise unlimited across
 * answers. All state lives here; the transport layer only relays the two views.</p>
 *
 * <p>Every public method is guarded by {@link #lock}, since actions for different players
 * arrive concurrently from separate WebSocket threads.</p>
 */
public class GetLitGame {

    public static final String PHASE_SELECT = "select";
    public static final String PHASE_ANSWER = "answer";
    public static final String PHASE_VOTE = "vote";
    public static final String PHASE_RESULTS = "results";
    public static final String PHASE_FINISHED = "finished";

    /** Longest accepted passage, to keep the wire and the big screen sane. */
    public static final int MAX_PASSAGE_LENGTH = 600;

    private static final int NORMAL_HEART_POINTS = 1;
    private static final int GOLDEN_HEART_POINTS = 3;

    private final Object lock = new Object();

    private final GetLitContent content;
    private final List<String> playerIds;
    private final Map<String, String> names;
    private final boolean familyFriendly;
    private final int totalRounds;
    private final Random random;

    private String phase = PHASE_SELECT;
    private int round = 0;
    private Prompt prompt;

    private final Set<String> usedPrompts = new HashSet<>();
    private final Map<String, String> chosenWork = new HashMap<>();

    private final Map<String, String> roundAnswers = new HashMap<>();
    private final Map<String, String> answerIdToPlayer = new HashMap<>();
    private final Map<String, String> playerToAnswerId = new HashMap<>();
    private final Map<String, Set<String>> normalHearts = new HashMap<>();
    private final Map<String, String> goldenHeart = new HashMap<>();

    private final Map<String, Integer> totals = new HashMap<>();
    private final Map<String, Integer> roundScores = new HashMap<>();

    public GetLitGame(
            final GetLitContent content,
            final List<String> playerIds,
            final Map<String, String> names,
            final boolean familyFriendly,
            final int totalRounds,
            final Random random) {
        this.content = content;
        this.playerIds = List.copyOf(playerIds);
        this.names = Map.copyOf(names);
        this.familyFriendly = familyFriendly;
        this.totalRounds = Math.max(1, totalRounds);
        this.random = random;
        for (final String id : this.playerIds) {
            totals.put(id, 0);
        }
    }

    // --- actions --------------------------------------------------------------

    public void chooseWork(final String playerId, final String workId) {
        synchronized (lock) {
            requirePhase(PHASE_SELECT);
            requirePlayer(playerId);
            final Book book = content.book(workId)
                    .filter(b -> b.allowed(familyFriendly))
                    .orElseThrow(() -> new LobbyException("no such work available"));
            chosenWork.put(playerId, book.id());
        }
    }

    public void submitAnswer(final String playerId, final String rawText) {
        synchronized (lock) {
            requirePhase(PHASE_ANSWER);
            requirePlayer(playerId);
            final String workId = chosenWork.get(playerId);
            if (workId == null) {
                throw new LobbyException("choose a work before answering");
            }
            final Book book = content.book(workId)
                    .orElseThrow(() -> new LobbyException("your work is no longer available"));

            final String text = rawText == null ? "" : normalize(rawText);
            if (text.isEmpty()) {
                throw new LobbyException("your answer cannot be empty");
            }
            if (text.length() > MAX_PASSAGE_LENGTH) {
                throw new LobbyException("your passage is too long (max " + MAX_PASSAGE_LENGTH + " characters)");
            }
            if (!content.bookText(book).contains(text)) {
                throw new LobbyException("that passage is not from your work");
            }
            if (roundAnswers.containsKey(playerId)) {
                throw new LobbyException("you already answered this round");
            }

            final String answerId = UUID.randomUUID().toString();
            roundAnswers.put(playerId, text);
            answerIdToPlayer.put(answerId, playerId);
            playerToAnswerId.put(playerId, answerId);
        }
    }

    public void castVote(final String playerId, final String answerId, final boolean golden) {
        synchronized (lock) {
            requirePhase(PHASE_VOTE);
            requirePlayer(playerId);
            final String author = answerIdToPlayer.get(answerId);
            if (author == null) {
                throw new LobbyException("no such answer");
            }
            if (author.equals(playerId)) {
                throw new LobbyException("you cannot heart your own answer");
            }
            if (golden) {
                if (goldenHeart.containsKey(playerId)) {
                    throw new LobbyException("you already spent your golden heart");
                }
                goldenHeart.put(playerId, answerId);
            } else if (!normalHearts.computeIfAbsent(answerId, k -> new LinkedHashSet<>()).add(playerId)) {
                throw new LobbyException("you already hearted that answer");
            }
        }
    }

    /**
     * Host-only: advance the state machine one step. Returns the new phase.
     */
    public String advance() {
        synchronized (lock) {
            switch (phase) {
                case PHASE_SELECT -> beginFirstRound();
                case PHASE_ANSWER -> {
                    requireEveryoneAnswered();
                    phase = PHASE_VOTE;
                }
                case PHASE_VOTE -> {
                    scoreRound();
                    phase = PHASE_RESULTS;
                }
                case PHASE_RESULTS -> {
                    if (round < totalRounds) {
                        beginRound(round + 1);
                    } else {
                        phase = PHASE_FINISHED;
                    }
                }
                case PHASE_FINISHED -> {
                    // nothing left to advance
                }
                default -> throw new LobbyException("cannot advance from " + phase);
            }
            return phase;
        }
    }

    private void beginFirstRound() {
        if (playerIds.isEmpty()) {
            throw new LobbyException("at least one player is required");
        }
        for (final String id : playerIds) {
            if (!chosenWork.containsKey(id)) {
                throw new LobbyException("everyone must choose a work first");
            }
        }
        beginRound(1);
    }

    private void beginRound(final int number) {
        round = number;
        pickPrompt();
        roundAnswers.clear();
        answerIdToPlayer.clear();
        playerToAnswerId.clear();
        normalHearts.clear();
        goldenHeart.clear();
        roundScores.clear();
        phase = PHASE_ANSWER;
    }

    private void requireEveryoneAnswered() {
        for (final String id : playerIds) {
            if (!roundAnswers.containsKey(id)) {
                throw new LobbyException("not everyone has answered yet");
            }
        }
    }

    private void pickPrompt() {
        final List<Prompt> candidates = content.prompts().stream()
                .filter(p -> p.allowed(familyFriendly))
                .filter(p -> !usedPrompts.contains(p.id()))
                .toList();
        if (candidates.isEmpty()) {
            throw new LobbyException("no prompts available");
        }
        prompt = candidates.get(random.nextInt(candidates.size()));
        usedPrompts.add(prompt.id());
    }

    private void scoreRound() {
        for (final String id : playerIds) {
            roundScores.put(id, 0);
        }
        for (final Map.Entry<String, String> entry : answerIdToPlayer.entrySet()) {
            final String answerId = entry.getKey();
            final String author = entry.getValue();
            final int normals = normalHearts.getOrDefault(answerId, Set.of()).size();
            final long goldens = goldenHeart.values().stream().filter(answerId::equals).count();
            final int points = normals * NORMAL_HEART_POINTS + (int) goldens * GOLDEN_HEART_POINTS;
            roundScores.merge(author, points, Integer::sum);
        }
        roundScores.forEach((id, points) -> totals.merge(id, points, Integer::sum));
    }

    // --- views ----------------------------------------------------------------

    public String phase() {
        synchronized (lock) {
            return phase;
        }
    }

    public int round() {
        synchronized (lock) {
            return round;
        }
    }

    public GetLitPublicView publicView() {
        synchronized (lock) {
            final List<WorkChoice> works = new ArrayList<>(playerIds.size());
            for (final String id : playerIds) {
                final String workId = chosenWork.get(id);
                works.add(new WorkChoice(id, names.get(id), workId, workId == null ? null : workTitle(workId)));
            }

            final List<AnswerView> answers = new ArrayList<>();
            if (phase.equals(PHASE_VOTE) || phase.equals(PHASE_RESULTS) || phase.equals(PHASE_FINISHED)) {
                final boolean revealed = phase.equals(PHASE_RESULTS) || phase.equals(PHASE_FINISHED);
                for (final String id : playerIds) {
                    final String answerId = playerToAnswerId.get(id);
                    if (answerId == null) {
                        continue;
                    }
                    final AnswerView view = revealed
                            ? new AnswerView(answerId, roundAnswers.get(id), names.get(id), pointsFor(answerId))
                            : new AnswerView(answerId, roundAnswers.get(id), null, null);
                    answers.add(view);
                }
            }

            final int submitted = (int) playerIds.stream().filter(roundAnswers::containsKey).count();
            final List<ScoreEntry> roundEntries = phase.equals(PHASE_RESULTS) || phase.equals(PHASE_FINISHED)
                    ? sortedScores(roundScores) : List.of();

            return new GetLitPublicView(
                    phase,
                    round,
                    totalRounds,
                    promptText(),
                    works,
                    submitted,
                    playerIds.size(),
                    answers,
                    roundEntries,
                    sortedScores(totals));
        }
    }

    public GetLitPrivateView privateView(final String playerId) {
        synchronized (lock) {
            final List<WorkView> library = phase.equals(PHASE_SELECT)
                    ? content.books().stream()
                            .filter(b -> b.allowed(familyFriendly))
                            .map(b -> new WorkView(b.id(), b.title(), b.author()))
                            .toList()
                    : List.of();

            final String workId = chosenWork.get(playerId);

            final List<String> hearted = new ArrayList<>();
            if (phase.equals(PHASE_VOTE)) {
                normalHearts.forEach((answerId, voters) -> {
                    if (voters.contains(playerId)) {
                        hearted.add(answerId);
                    }
                });
                final String goldenTarget = goldenHeart.get(playerId);
                if (goldenTarget != null) {
                    hearted.add(goldenTarget);
                }
            }

            return new GetLitPrivateView(
                    phase,
                    round,
                    totalRounds,
                    promptText(),
                    library,
                    workId,
                    workId == null ? null : workTitle(workId),
                    playerToAnswerId.get(playerId),
                    roundAnswers.get(playerId),
                    phase.equals(PHASE_VOTE) && !goldenHeart.containsKey(playerId),
                    hearted,
                    roundScores.getOrDefault(playerId, 0),
                    totals.getOrDefault(playerId, 0));
        }
    }

    private int pointsFor(final String answerId) {
        final int normals = normalHearts.getOrDefault(answerId, Set.of()).size() * NORMAL_HEART_POINTS;
        final long goldens = goldenHeart.values().stream().filter(answerId::equals).count();
        return normals + (int) goldens * GOLDEN_HEART_POINTS;
    }

    private String promptText() {
        return switch (phase) {
            case PHASE_ANSWER, PHASE_VOTE, PHASE_RESULTS -> prompt == null ? null : prompt.text();
            default -> null;
        };
    }

    private String workTitle(final String workId) {
        return content.book(workId).map(Book::title).orElse(null);
    }

    private List<ScoreEntry> sortedScores(final Map<String, Integer> scores) {
        final List<ScoreEntry> entries = new ArrayList<>(playerIds.size());
        for (final String id : playerIds) {
            entries.add(new ScoreEntry(id, names.get(id), scores.getOrDefault(id, 0)));
        }
        entries.sort(Comparator.comparingInt(ScoreEntry::score).reversed());
        return entries;
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

}
