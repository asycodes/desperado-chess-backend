package com.desperado.desperado_chess_backend.match.engine;

import com.github.bhlangonijr.chesslib.PieceType;

public final class PieceValues {

    public static final int CHECKMATE_BONUS = 15;

    private PieceValues() {}

    public static int of(PieceType type) {
        if (type == null) return 0;
        return switch (type) {
            case PAWN -> 1;
            case KNIGHT, BISHOP -> 3;
            case ROOK -> 5;
            case QUEEN -> 9;
            default -> 0;
        };
    }
}