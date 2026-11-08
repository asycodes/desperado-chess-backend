package com.desperado.desperado_chess_backend.room.DTO;


public record PlayerResponse(Long id, String nickname, int position,boolean isHost,boolean isBot ) {}