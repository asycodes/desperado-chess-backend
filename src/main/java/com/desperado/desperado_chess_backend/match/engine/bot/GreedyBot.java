package com.desperado.desperado_chess_backend.match.engine.bot;

import com.desperado.desperado_chess_backend.match.engine.PieceValues;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Easy bot: checkmates if it can, otherwise captures the most valuable piece available,
 * otherwise plays a random legal move.
 * {TO BE REPLACED IN FUTURE VERSIONS}
 */
public class GreedyBot implements BotStrategy {

    private static final int CHECKMATE_SCORE = 1000;

    private final Random random;

    public GreedyBot(Random random) {
        this.random = random;
    }

    @Override
    public String chooseMove(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);

        List<Move> moves = board.legalMoves();
        if (moves.isEmpty()) {
            throw new IllegalStateException("No legal moves in position " + fen);
        }

        int bestScore = Integer.MIN_VALUE;
        List<Move> bestMoves = new ArrayList<>();
        for (Move move : moves) {
            int score = score(board, move);
            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }
        Move chosen = bestMoves.get(random.nextInt(bestMoves.size()));
        return chosen.toString();
    }

    private int score(Board board, Move move) {
        int score = captureValue(board, move);

        board.doMove(move);
        if (board.isMated()) {
            score = CHECKMATE_SCORE;
        }
        board.undoMove();

        return score;
    }

    private int captureValue(Board board, Move move) {
        Piece target = board.getPiece(move.getTo());
        if (target != Piece.NONE) {
            return PieceValues.of(target.getPieceType());
        }
        Piece moving = board.getPiece(move.getFrom());
        boolean enPassant = moving.getPieceType() == PieceType.PAWN
                && move.getFrom().getFile() != move.getTo().getFile();
        return enPassant ? 1 : 0;
    }
}