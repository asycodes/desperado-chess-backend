package com.desperado.desperado_chess_backend.match.controller;

import com.desperado.desperado_chess_backend.match.dto.MoveRequest;
import com.desperado.desperado_chess_backend.match.engine.MatchSnapshot;
import com.desperado.desperado_chess_backend.match.engine.MoveResult;
import com.desperado.desperado_chess_backend.match.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/{code}")
    public MatchSnapshot getMatch(@PathVariable String code) {
        return matchService.getSnapshot(code);
    }

    @PostMapping("/{code}/moves")
    public MoveResult makeMove(@PathVariable String code, @Valid @RequestBody MoveRequest request) {
        return matchService.makeMove(code, request.playerId(), request.boardId(), request.move());
    }
}