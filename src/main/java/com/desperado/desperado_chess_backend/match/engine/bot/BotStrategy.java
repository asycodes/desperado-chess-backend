package com.desperado.desperado_chess_backend.match.engine.bot;

/** Chooses a move for the side to move in the given position. */
public interface BotStrategy {
    /** @return a legal move in UCI notation, e.g. "e2e4" */
    String chooseMove(String fen);
}