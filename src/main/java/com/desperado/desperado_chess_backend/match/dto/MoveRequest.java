package com.desperado.desperado_chess_backend.match.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MoveRequest(
        @NotNull Long playerId,
        @NotNull Integer boardId,
        @NotNull @Pattern(regexp = "^[a-h][1-8][a-h][1-8][qrbnQRBN]?$", message = "Move must be in UCI format, e.g. e2e4 or e7e8q")
        String move
) {}