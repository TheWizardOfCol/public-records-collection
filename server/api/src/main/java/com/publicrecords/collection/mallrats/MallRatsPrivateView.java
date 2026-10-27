package com.publicrecords.collection.mallrats;

import com.publicrecords.collection.getlit.ScoreEntry;

import java.util.List;
import java.util.Map;

/**
 * A player's private Mall Rats view: their own ratings and shopping satchel,
 * plus whom they still owe.
 */
public record MallRatsPrivateView(
        String phase,
        String storeId,
        String storeName,
        String storeMeter,
        int cycle,
        List<MallRatsPoolGift> pool,
        int targetScore,
        Map<String, Integer> myRatings,
        List<String> myRatesOutstanding,
        Map<String, String> myAssignments,
        List<String> myRecipientsOutstanding,
        List<ScoreEntry> roundScores,
        List<ScoreEntry> scores,
        int myRoundPoints,
        String myReceivedLine) {

}
