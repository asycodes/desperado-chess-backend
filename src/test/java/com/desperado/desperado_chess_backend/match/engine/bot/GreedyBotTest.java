package com.desperado.desperado_chess_backend.match.engine.bot;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyBotTest {

    private final GreedyBot bot = new GreedyBot(new Random(42));

    @Test
    void capturesTheMostValuablePiece() {
        // White rook on a1 can take the black queen on a8
        String fen = "q3k3/8/8/8/8/8/8/R3K3 w - - 0 1";
        assertThat(bot.chooseMove(fen)).isEqualTo("a1a8");
    }

    @Test
    void prefersCheckmateOverEverythingElse() {
        // Back-rank mate: Ra1-a8 is checkmate
        String fen = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1";
        assertThat(bot.chooseMove(fen)).isEqualTo("a1a8");
    }

    @Test
    void alwaysReturnsALegalMoveFromTheStart() {
        String start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        assertThat(bot.chooseMove(start)).matches("^[a-h][1-8][a-h][1-8]$");
    }
}