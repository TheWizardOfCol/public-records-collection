package com.publicrecords.collection.mallrats;

import com.publicrecords.collection.getlit.ScoreEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The public (host-screen) view of a Mall Rats game.
 *
 * <p>The game runs cycles until someone crosses the winning score: each cycle
 * draws a fresh pool, everyone rates it, everyone shops for everyone else, and
 * a cycle's scores fold into the one shared score bank.</p>
 */
@Schema(description = "The public (host-screen) view of a Mall Rats game.")
public record MallRatsPublicView(
        @Schema(description = "brief | rate | shop | results | finished") String phase,
        @Schema(description = "Store id the host picked.") String storeId,
        @Schema(description = "Store name (as on the sign).") String storeName,
        @Schema(description = "Price meter: $ | $$ | $$$ | ∞.") String storeMeter,
        @Schema(description = "Which shopping cycle this is, 1-based.") int cycle,
        @Schema(description = "Gifts in this cycle's pool.") List<MallRatsPoolGift> pool,
        @Schema(description = "Winning score (first to reach it wins).") int targetScore,
        @Schema(description = "How many players have finished rating every gift.") int ratedBy,
        @Schema(description = "How many players have shopped for everyone.") int shoppedBy,
        @Schema(description = "Number of players in the game.") int players,
        @Schema(description = "Points this cycle, highest first (results only; empty before).") List<ScoreEntry> roundScores,
        @Schema(description = "The shared score bank, highest first.") List<ScoreEntry> scores,
        @Schema(description = "Winner's player id (finished only).") String winnerId,
        @Schema(description = "Winner's display name (finished only).") String winnerName) {

}
