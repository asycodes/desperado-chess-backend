package com.desperado.desperado_chess_backend.match.engine;

import java.util.List;
import java.util.Map;

public record MatchSnapshot(
        String roomCode,
        String status,
        long endsAtEpochMs,
        List<SeatScore> seats,
        List<BoardState> boards
) {
    public record SeatScore(long playerId, String name, int position, boolean bot, int score, boolean leader) {}

    public record BoardState(int id, long whiteId, long blackId, String fen,
                             long playerToMove, long turnDeadlineEpochMs, Map<Long, Integer> points) {}
}