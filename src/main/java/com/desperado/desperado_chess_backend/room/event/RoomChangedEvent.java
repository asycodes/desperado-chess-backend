package com.desperado.desperado_chess_backend.room.event;

import com.desperado.desperado_chess_backend.room.DTO.RoomResponse;

public record RoomChangedEvent(RoomResponse room) {}