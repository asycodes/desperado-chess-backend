package com.desperado.desperado_chess_backend.room.service;

import com.desperado.desperado_chess_backend.room.DTO.*;
import com.desperado.desperado_chess_backend.room.entity.Player;
import com.desperado.desperado_chess_backend.room.entity.Room;
import com.desperado.desperado_chess_backend.room.event.RoomChangedEvent;
import com.desperado.desperado_chess_backend.room.repository.PlayerRepository;
import com.desperado.desperado_chess_backend.room.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
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
        Player host = playerRepository.save(new Player(room, request.nickname().trim(), 1, false));
        return new JoinRoomResponse(room.getCode(), host.getId());
        }
    @Transactional
    public JoinRoomResponse joinRoom(String code, JoinRoomRequest request) {
        Room room = findRoom(code);
        Player player = takeSeat(room, request.nickname().trim(), false);
        return new JoinRoomResponse(room.getCode(), player.getId());
    }

    @Transactional
    public PlayerResponse addBot(String code, AddBotRequest request) {
        Room room = findRoom(code);

        Player requester = playerRepository.findById(request.requesterId())
                .filter(p -> p.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not in this room"));

        if (!requester.isHost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can add bots");
        }

        Player bot = takeSeat(room, nextBotName(room), true);
        return new PlayerResponse(bot.getId(), bot.getName(), bot.getPosition(), bot.isHost(),bot.isBot());
    }

    // Shared by humans and bots: all the seat rules live here, once.
    private Player takeSeat(Room room, String name, boolean isBot) {
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
        return playerRepository.save(new Player(room, name, position, isBot));
    }

    private String nextBotName(Room room) {
        for (int i = 1; i <= MAX_PLAYERS; i++) {
            String candidate = "Bot " + i;
            if (!playerRepository.existsByRoomAndNameIgnoreCase(room, candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
    }
    @Transactional(readOnly = true)
    public RoomResponse getRoom(String code) {
        Room room = findRoom(code);
        List<PlayerResponse> players = playerRepository.findByRoomOrderByPositionAsc(room).stream()
                .map(p -> new PlayerResponse(p.getId(), p.getName(), p.getPosition(), p.isHost(), p.isBot()))
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



    private final ApplicationEventPublisher eventPublisher; // org.springframework.context.ApplicationEventPublisher

    private void publishRoomChanged(Room room) {
        eventPublisher.publishEvent(new RoomChangedEvent(buildRoomResponse(room)));
    }

    private RoomResponse buildRoomResponse(Room room) {
    List<PlayerResponse> players = playerRepository.findByRoomOrderByPositionAsc(room).stream()
            .map(p -> new PlayerResponse(p.getId(), p.getName(), p.getPosition(), p.isBot(), p.isHost()))
            .toList();
    return new RoomResponse(room.getCode(), room.getMode(), room.isActive(), players);
    }


    private static final int MIN_PLAYERS = 2;
    @Transactional
    public RoomResponse startMatch(String code, StartMatchRequest request) {
        Room room = findRoom(code);
        requireHost(room, request.requesterId());

        if (room.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match already started");
        }
        if (playerRepository.findByRoomOrderByPositionAsc(room).size() < MIN_PLAYERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Need at least " + MIN_PLAYERS + " players");
        }

        room.setMode(request.mode());
        room.setActive(true);
        // No save() call needed: see the note below.

        RoomResponse response = buildRoomResponse(room);
        eventPublisher.publishEvent(new RoomChangedEvent(response));
        return response;
    }

    private void requireHost(Room room, Long requesterId) {
        Player requester = playerRepository.findById(requesterId)
                .filter(p -> p.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not in this room"));
        if (!requester.isHost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can do this");
        }
}
}