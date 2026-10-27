package com.publicrecords.collection.mallrats;

import com.publicrecords.collection.getlit.ScoreEntry;
import com.publicrecords.collection.lobby.LobbyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mall Rats: rate the gift pool, then shop for your friends. One shared score
 * bank runs across cycles — a cycle draws {@value #POOL_SIZE} gifts, everyone
 * rates every gift (0–5 stars), everyone then assigns a gift to every other
 * player, and both sides of the trade pay out the gift's average star rating.
 * First player to reach the winning score closes the session.
 */
public class MallRatsGame {

    private static final Logger logger = LoggerFactory.getLogger(MallRatsGame.class);

    public static final String PHASE_BRIEF = "brief";
    public static final String PHASE_RATE = "rate";
    public static final String PHASE_SHOP = "shop";
    public static final String PHASE_RESULTS = "results";
    public static final String PHASE_FINISHED = "finished";

    /** One star one point — the score that closes the session. */
    public static final int TARGET_SCORE = 30;

    /** Pool size for each cycle (MallRatsContent#POOL_SIZE-sized). */
    private static final int POOL_SIZE = MallRatsContent.POOL_SIZE;

    private final Object lock = new Object();

    private final Store store;
    private final Map<String, String> playerNames;
    private final List<String> playerIds;
    private final Random random;

    private String phase = PHASE_BRIEF;
    private int cycle = 0;

    /** Gifts drawn this cycle, presentation order. */
    private List<Gift> pool = List.of();

    /** Gifts already screened this session — a cycle never repeats them until the store runs dry. */
    private final List<String> screenedIds = new ArrayList<>();

    /** playerId -> (giftId -> stars 0..5) for the current cycle. */
    private final Map<String, Map<String, Integer>> ratings = new LinkedHashMap<>();

    /** giverId -> (recipientId -> giftId) for the current cycle. */
    private final Map<String, Map<String, String>> assignments = new LinkedHashMap<>();

    /** Cumulative, shared score bank across cycles. */
    private final Map<String, Integer> totals = new LinkedHashMap<>();

    /** This cycle's awarded points, populated in results. */
    private final Map<String, Integer> roundScores = new LinkedHashMap<>();

    /** Points a player earned receiving gifts this cycle (results). */
    private final Map<String, Integer> receivedPoints = new LinkedHashMap<>();

    /** Points a player earned giving gifts this cycle (results). */
    private final Map<String, Integer> givenPoints = new LinkedHashMap<>();

    /** Gift id -> who assigned it to this player (results, for the received line). */
    private final Map<String, Map<String, List<String>>> receivedFrom = new LinkedHashMap<>();

    private String winnerId;

    public MallRatsGame(
            final Store store,
            final List<String> playerIds,
            final Map<String, String> playerNames,
            final Random random) {
        this.store = store;
        this.playerIds = List.copyOf(playerIds);
        this.playerNames = Map.copyOf(playerNames);
        this.random = random;
        for (final String id : playerIds) {
            totals.put(id, 0);
        }
        beginCycle();
    }

    /** The public (host-screen) view — safe to call from any thread. */
    public MallRatsPublicView publicView() {
        synchronized (lock) {
            final boolean reveal = phase.equals(PHASE_RESULTS) || phase.equals(PHASE_FINISHED);
            final List<MallRatsPoolGift> gifts = new ArrayList<>(pool.size());
            for (final Gift gift : pool) {
                gifts.add(new MallRatsPoolGift(
                        gift.id(),
                        gift.name(),
                        gift.image(),
                        reveal ? averageStars(gift.id()) : null));
            }
            String winnerName = null;
            if (winnerId != null) {
                winnerName = playerNames.get(winnerId);
            }
            return new MallRatsPublicView(
                    phase,
                    store.id(),
                    store.name(),
                    store.meter(),
                    cycle,
                    List.copyOf(gifts),
                    TARGET_SCORE,
                    countComplete(ratings, () -> pool.size()),
                    countCompleteAssignments(),
                    playerIds.size(),
                    sortedScores(roundScores),
                    sortedScores(totals),
                    winnerId,
                    winnerName);
        }
    }

    /** A player's private view — safe to call from any thread. */
    public MallRatsPrivateView privateView(final String playerId) {
        synchronized (lock) {
            final boolean reveal = phase.equals(PHASE_RESULTS) || phase.equals(PHASE_FINISHED);
            final List<MallRatsPoolGift> gifts = new ArrayList<>(pool.size());
            for (final Gift gift : pool) {
                gifts.add(new MallRatsPoolGift(
                        gift.id(),
                        gift.name(),
                        gift.image(),
                        reveal ? averageStars(gift.id()) : null));
            }
            final Map<String, Integer> mine = new LinkedHashMap<>();
            final Map<String, Integer> rated = ratings.get(playerId);
            if (rated != null) {
                mine.putAll(rated);
            }
            final Map<String, String> assignmentsMine = new LinkedHashMap<>();
            final Map<String, String> assigned = assignments.get(playerId);
            if (assigned != null) {
                assignmentsMine.putAll(assigned);
            }
            final List<String> outstandingRates = new ArrayList<>();
            for (final Gift gift : pool) {
                if (!mine.containsKey(gift.id())) {
                    outstandingRates.add(gift.id());
                }
            }
            final List<String> outstandingRecipients = new ArrayList<>();
            if (phase.equals(PHASE_SHOP)) {
                for (final String other : playerIds) {
                    if (!other.equals(playerId) && !assignmentsMine.containsKey(other)) {
                        outstandingRecipients.add(other);
                    }
                }
            }
            final List<String> received = new ArrayList<>();
            final Map<String, List<String>> gotFrom = receivedFrom.get(playerId);
            if (gotFrom != null) {
                for (final Map.Entry<String, List<String>> e : gotFrom.entrySet()) {
                    final String who = playerNames.getOrDefault(e.getValue().get(0), "?");
                    final String name = pool.stream().filter(g -> g.id().equals(e.getKey()))
                            .findFirst().map(Gift::name).orElse("?");
                    received.add(name + " (from " + who + ")");
                }
            }
            return new MallRatsPrivateView(
                    phase,
                    store.id(),
                    store.name(),
                    store.meter(),
                    cycle,
                    List.copyOf(gifts),
                    TARGET_SCORE,
                    Map.copyOf(mine),
                    List.copyOf(outstandingRates),
                    Map.copyOf(assignmentsMine),
                    List.copyOf(outstandingRecipients),
                    sortedScores(roundScores),
                    sortedScores(totals),
                    roundScores.getOrDefault(playerId, 0),
                    String.join(" · ", received));
        }
    }

    /**
     * Player action: stars a pool gift (0–5). Re-rating overwrites the earlier stars.
     */
    public void rateGift(final String playerId, final String giftId, final int stars) {
        synchronized (lock) {
            requirePlayer(playerId);
            requirePhase(PHASE_RATE);
            final Gift gift = giftInPool(giftId);
            if (stars < 0 || stars > 5) {
                throw new LobbyException("stars must be 0 to 5");
            }
            if (ratings.containsKey(playerId) && ratings.get(playerId).containsKey(giftId)) {
                throw new LobbyException("you already rated that gift");
            }
            ratings.computeIfAbsent(playerId, k -> new LinkedHashMap<>()).put(gift.id(), stars);
        }
    }

    /**
     * Player action: assign a pool gift to another player. One assignment per
     * recipient; the same gift may be given by one shopper at most once per cycle.
     */
    public void shopGift(final String playerId, final String recipientId, final String giftId) {
        synchronized (lock) {
            requirePlayer(playerId);
            requirePhase(PHASE_SHOP);
            if (recipientId == null || recipientId.equals(playerId)) {
                throw new LobbyException("pick someone else to shop for");
            }
            if (!playerIds.contains(recipientId)) {
                throw new LobbyException("no such player to shop for");
            }
            final Gift gift = giftInPool(giftId);
            final Map<String, String> mine = assignments.computeIfAbsent(playerId, k -> new LinkedHashMap<>());
            if (mine.containsKey(recipientId)) {
                throw new LobbyException("you already picked a gift for that player");
            }
            final long sameGift = mine.containsValue(gift.id())
                    ? mine.values().stream().filter(v -> v.equals(gift.id())).count()
                    : 0;
            if (sameGift >= 1) {
                throw new LobbyException("you already gave that gift to someone");
            }
            mine.put(recipientId, gift.id());
        }
    }

    /**
     * Host action: advance one phase. Transitions obey the game's own guards:
     * rating may close only once everyone has starred the pool, shopping may
     * close only once everyone has shopped for everyone else.
     */
    public void advance() {
        synchronized (lock) {
            switch (phase) {
                case PHASE_BRIEF -> phase = PHASE_RATE;
                case PHASE_RATE -> {
                    requireEveryoneRated();
                    phase = PHASE_SHOP;
                }
                case PHASE_SHOP -> {
                    requireEveryoneShopped();
                    scoreRound();
                    phase = winnerId != null ? PHASE_FINISHED : PHASE_RESULTS;
                }
                case PHASE_RESULTS -> {
                    requireNothingDecided();
                    beginCycle();
                    phase = PHASE_BRIEF;
                }
                case PHASE_FINISHED -> { /* done */ }
                default -> throw new LobbyException("cannot advance from " + phase);
            }
        }
    }

    private void beginCycle() {
        cycle++;
        pool = drawPool();
        ratings.clear();
        assignments.clear();
        roundScores.clear();
        receivedPoints.clear();
        givenPoints.clear();
        receivedFrom.clear();
        for (final String id : playerIds) {
            roundScores.put(id, 0);
        }
    }

    /** The next cycle's pool: unseen gifts first; when the store is screened out, reshuffle everything. */
    private List<Gift> drawPool() {
        final List<Gift> unseen = new ArrayList<>();
        for (final Gift gift : store.gifts()) {
            if (!screenedIds.contains(gift.id())) {
                unseen.add(gift);
            }
        }
        if (unseen.size() < POOL_SIZE) {
            screenedIds.clear();
            unseen.clear();
            unseen.addAll(store.gifts());
        }
        Collections.shuffle(unseen, random);
        final List<Gift> drawn = new ArrayList<>(unseen.subList(0, Math.min(POOL_SIZE, unseen.size())));
        final List<Gift> presentation = new ArrayList<>(drawn);
        Collections.shuffle(presentation, random);
        for (final Gift gift : presentation) {
            screenedIds.add(gift.id());
        }
        return presentation;
    }

    private void scoreRound() {
        for (final String id : playerIds) {
            roundScores.put(id, 0);
            receivedPoints.put(id, 0);
            givenPoints.put(id, 0);
        }
        for (final Map.Entry<String, Map<String, String>> e : assignments.entrySet()) {
            final String giver = e.getKey();
            for (final Map.Entry<String, String> a : e.getValue().entrySet()) {
                final String recipient = a.getKey();
                final int stars = (int) Math.round(averageStars(a.getValue()));
                receivedPoints.merge(recipient, stars, Integer::sum);
                givenPoints.merge(giver, stars, Integer::sum);
                receivedFrom.computeIfAbsent(recipient, k -> new LinkedHashMap<>())
                        .computeIfAbsent(a.getValue(), k -> new ArrayList<>())
                        .add(giver);
            }
        }
        for (final String id : playerIds) {
            final int points = receivedPoints.get(id) + givenPoints.get(id);
            roundScores.put(id, points);
            totals.merge(id, points, Integer::sum);
        }
        final List<ScoreEntry> sorted = sortedScores(totals);
        if (!sorted.isEmpty() && sorted.get(0).score() >= TARGET_SCORE) {
            winnerId = sorted.get(0).playerId();
            logger.info("Mall Rats session closed: {} wins with {}", winnerId, sorted.get(0).score());
        }
    }

    private double averageStars(final String giftId) {
        int sum = 0;
        int count = 0;
        for (final Map<String, Integer> m : ratings.values()) {
            final Integer stars = m.get(giftId);
            if (stars != null) {
                sum += stars;
                count++;
            }
        }
        return count == 0 ? 0.0 : (double) sum / count;
    }

    private int countComplete(final Map<String, Map<String, Integer>> map, final java.util.function.Supplier<Integer> need) {
        int done = 0;
        final int required = need.get();
        for (final String id : playerIds) {
            final Map<String, Integer> m = map.get(id);
            if (m != null && m.size() >= required) {
                done++;
            }
        }
        return done;
    }

    private int countCompleteAssignments() {
        int done = 0;
        final int required = playerIds.size() - 1;
        for (final String id : playerIds) {
            final Map<String, String> m = assignments.get(id);
            if (m != null && m.size() >= required) {
                done++;
            }
        }
        return done;
    }

    private void requireEveryoneRated() {
        for (final String id : playerIds) {
            final Map<String, Integer> m = ratings.get(id);
            if (m == null || m.size() < pool.size()) {
                throw new LobbyException("not everyone has rated the pool yet");
            }
        }
    }

    private void requireEveryoneShopped() {
        final int required = playerIds.size() - 1;
        for (final String id : playerIds) {
            final Map<String, String> m = assignments.get(id);
            if (required > 0 && (m == null || m.size() < required)) {
                throw new LobbyException("not everyone has shopped for everyone else yet");
            }
        }
    }

    private void requireNothingDecided() {
        if (playerIds.size() < 2) {
            throw new LobbyException("mall rats needs at least two players");
        }
    }

    private Gift giftInPool(final String giftId) {
        if (giftId == null) {
            throw new LobbyException("giftId is required");
        }
        return pool.stream().filter(g -> g.id().equals(giftId)).findFirst()
                .orElseThrow(() -> new LobbyException("no such gift in the pool"));
    }

    private void requirePlayer(final String playerId) {
        if (playerId == null || !playerIds.contains(playerId)) {
            throw new LobbyException("you are not in this game");
        }
    }

    private void requirePhase(final String expected) {
        if (!phase.equals(expected)) {
            throw new LobbyException("wrong phase: " + phase);
        }
    }

    private List<ScoreEntry> sortedScores(final Map<String, Integer> scores) {
        final List<ScoreEntry> list = new ArrayList<>();
        for (final String id : playerIds) {
            list.add(new ScoreEntry(id, playerNames.getOrDefault(id, "?"), scores.getOrDefault(id, 0)));
        }
        list.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return list;
    }

}
