package com.desperado.desperado_chess_backend.room;

import com.desperado.desperado_chess_backend.room.DTO.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import lombok.RequiredArgsConstructor;


//need to consider racing conditions, 2 players joining at the same time

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<JoinRoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        JoinRoomResponse response = roomService.createRoom(request);
        return ResponseEntity.created(URI.create("/api/rooms/" + response.code())).body(response);
    }

    @PostMapping("/{code}/players")
    public ResponseEntity<JoinRoomResponse> joinRoom(@PathVariable String code,
                                                     @Valid @RequestBody JoinRoomRequest request) {
        JoinRoomResponse response = roomService.joinRoom(code, request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{code}")
    public RoomResponse getRoom(@PathVariable String code) {
        return roomService.getRoom(code);
    }

    @PostMapping("/{code}/bots")
    public ResponseEntity<PlayerResponse> addBot(@PathVariable String code, @Valid @RequestBody AddBotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.addBot(code, request));
    }
}