package com.desperado.desperado_chess_backend.match.engine;

import com.github.bhlangonijr.chesslib.PieceType;

/** What happened on one board after one move. Internal to the engine. */
record MoveOutcome(PieceType captured, boolean checkmate, boolean drawn) {}