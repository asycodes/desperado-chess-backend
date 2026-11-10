package com.desperado.desperado_chess_backend.match.engine;

/** What the engine reports back after a player's move. */
public record MoveResult(int pointsAwarded, boolean bountyApplied, boolean checkmate) {}