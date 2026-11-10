package com.desperado.desperado_chess_backend.match.engine;

public record Seat(long playerId, String name, int position, boolean bot) {}