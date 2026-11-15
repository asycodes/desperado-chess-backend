package com.desperado.desperado_chess_backend.match.engine;

/**
 * One thing that happened on a board, in order. Kept for post-match analysis.
 *
 * <p>{@code fenBefore} is the position before the event, so every event can be analysed on its own,
 * without replaying the game. That matters because a timed-out turn changes the side to move
 * without a chess move, which a simple replay of UCI moves could not reproduce.
 */
public record BoardEvent(
        Type type,
        long playerId,
        String fenBefore,
        String uci,
        long thinkMs,
        String captured,
        int points,
        boolean bounty, 
        boolean checkmate,
        long atEpochMs) {

    public enum Type { MOVE, TIMEOUT_PASS, TIMEOUT_MATE }

    static BoardEvent move(long playerId, String fenBefore, String uci, long thinkMs,
                           String captured, int points, boolean bounty, boolean checkmate, long atEpochMs) {
        return new BoardEvent(Type.MOVE, playerId, fenBefore, uci, thinkMs,
                captured, points, bounty, checkmate, atEpochMs);
    }

    static BoardEvent timeout(Type type, long playerId, String fenBefore, long thinkMs, long atEpochMs) {
        return new BoardEvent(type, playerId, fenBefore, null, thinkMs, null, 0, false, false, atEpochMs);
    }
}
