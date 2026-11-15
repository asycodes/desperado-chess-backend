package com.desperado.desperado_chess_backend.match.engine;

import java.util.List;

/** Everything that happened in a finished match: sent to the coach service for analysis. */
public record MatchRecord(
        String matchId,
        String roomCode,
        long startedAtMs,
        long endedAtMs,
        List<PlayerRecord> players,
        List<GameRecord> games) {

    public record PlayerRecord(long playerId, String name, int position, boolean bot, int score) {}
}
