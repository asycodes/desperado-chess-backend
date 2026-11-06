package com.desperado.desperado_chess_backend.room;

import com.desperado.desperado_chess_backend.room.DTO.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int MAX_PLAYERS = 4;
    private static final int CODE_LENGTH = 4;
    private static final int MAX_CODE_ATTEMPTS = 20;
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public JoinRoomResponse createRoom(CreateRoomRequest request) {
        Room room = roomRepository.save(new Room(generateUniqueCode()));
        Player host = playerRepository.save(new Player(room, request.nickname().trim(), 1));
        return new JoinRoomResponse(room.getCode(), host.getId());
    }

    @Transactional
    public JoinRoomResponse joinRoom(String code, JoinRoomRequest request) {
        Room room = findRoom(code);
        String name = request.nickname().trim();

        if (room.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A match is in progress in this room");
        }

        List<Player> players = playerRepository.findByRoomOrderByPositionAsc(room);
        if (players.size() >= MAX_PLAYERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
        }
        if (playerRepository.existsByRoomAndNameIgnoreCase(room, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That nickname is already taken in this room");
        }

        int position = firstFreePosition(players);
        Player player = playerRepository.save(new Player(room, name, position));
        return new JoinRoomResponse(room.getCode(), player.getId());
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(String code) {
        Room room = findRoom(code);
        List<PlayerResponse> players = playerRepository.findByRoomOrderByPositionAsc(room).stream()
                .map(p -> new PlayerResponse(p.getId(), p.getName(), p.getPosition(), p.isHost()))
                .toList();
        return new RoomResponse(room.getCode(), room.getMode(), room.isActive(), players);
    }

    private Room findRoom(String code) {
        return roomRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    private int firstFreePosition(List<Player> players) {
        for (int pos = 1; pos <= MAX_PLAYERS; pos++) {
            final int candidate = pos;
            if (players.stream().noneMatch(p -> p.getPosition() == candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!roomRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique room code");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return sb.toString();
    }
}