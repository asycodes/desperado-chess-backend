package com.desperado.desperado_chess_backend.room.DTO;

import jakarta.validation.constraints.NotNull;

public record AddBotRequest(
        @NotNull Long requesterId
) {}