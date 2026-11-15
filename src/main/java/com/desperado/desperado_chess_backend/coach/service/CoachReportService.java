package com.desperado.desperado_chess_backend.coach.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the coach report for each room's latest match (in memory, like the match itself)
 * and pushes every change to {@code /topic/matches/{code}/report}.
 *
 * <p>Every update names the match it belongs to. An update for an older match (for example a slow
 * report arriving after a rematch has finished) is ignored, so a room never shows the wrong report.
 */
@Service
public class CoachReportService {

    public enum Status { PENDING, READY, FAILED }

    /** What clients receive. {@code report} is the coach's JSON, passed through unchanged; null until READY. */
    public record View(String matchId, Status status, JsonNode report) {}

    private final Map<String, View> byRoom = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messaging;

    public CoachReportService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void markPending(String roomCode, String matchId) {
        View view = new View(matchId, Status.PENDING, null);
        byRoom.put(roomCode, view);
        publish(roomCode, view);
    }

    /** Stores a finished report. Empty if the report is for a match that is no longer this room's latest. */
    public Optional<View> complete(String roomCode, String matchId, JsonNode report) {
        return update(roomCode, matchId, new View(matchId, Status.READY, report));
    }

    public Optional<View> fail(String roomCode, String matchId) {
        return update(roomCode, matchId, new View(matchId, Status.FAILED, null));
    }

    public Optional<View> get(String roomCode) {
        return Optional.ofNullable(byRoom.get(roomCode));
    }

    private Optional<View> update(String roomCode, String matchId, View next) {
        AtomicReference<View> applied = new AtomicReference<>();
        // computeIfPresent runs atomically for this key: the matchId check and the write can't be interleaved
        byRoom.computeIfPresent(roomCode, (code, current) -> {
            if (!current.matchId().equals(matchId)) return current; // stale: leave it alone
            applied.set(next);
            return next;
        });
        Optional<View> result = Optional.ofNullable(applied.get());
        result.ifPresent(view -> publish(roomCode, view));
        return result;
    }

    private void publish(String roomCode, View view) {
        messaging.convertAndSend("/topic/matches/" + roomCode + "/report", view);
    }
}
