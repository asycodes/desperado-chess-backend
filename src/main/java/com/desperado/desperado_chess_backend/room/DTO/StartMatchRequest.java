package com.desperado.desperado_chess_backend.room.DTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;


public record StartMatchRequest(
        @NotNull Long requesterId,
        @NotBlank @Pattern(regexp = "FFA|FFA_ECONOMY") String mode
) {}