package com.desperado.desperado_chess_backend.match.engine;

import java.util.List;

/**
 * One game played on a board. A board restarts after checkmate or a draw,
 * so a single board can produce several games in one match.
 */
public record GameRecord(
        int boardId,
        int gameNumber,
        long whiteId,
        long blackId,
        Result result,
        List<BoardEvent> events) {

    public enum Result { WHITE_WON, BLACK_WON, DRAW, UNFINISHED }
}
