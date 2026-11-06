package com.desperado.desperado_chess_backend.room.DTO;

import java.util.List;

public record RoomResponse(String code, String mode, boolean active, List<PlayerResponse> players) {}