package com.desperado.desperado_chess_backend.match.service;

import com.desperado.desperado_chess_backend.match.engine.Match;
import com.desperado.desperado_chess_backend.match.engine.MatchRecord;
import com.desperado.desperado_chess_backend.match.engine.MatchSnapshot;
import com.desperado.desperado_chess_backend.match.engine.MoveResult;
import com.desperado.desperado_chess_backend.match.engine.Seat;
import com.desperado.desperado_chess_backend.match.engine.bot.BotStrategy;
import com.desperado.desperado_chess_backend.match.event.MatchFinishedEvent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MatchService {

    private final Map<String, Match> matches = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messaging;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration matchLength;
    private final Duration moveTimeout;
    private final BotStrategy botStrategy;
    private final Duration botThinkTime;
    private final Duration botCooldown;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "match-ticker");
        thread.setDaemon(true);
        return thread;
    });

    public MatchService(SimpMessagingTemplate messaging,
                        ApplicationEventPublisher events,
                        Clock clock,
                        BotStrategy botStrategy,
                        @Value("${desperado.match.length:10m}") Duration matchLength,
                        @Value("${desperado.match.move-timeout:60s}") Duration moveTimeout,
                        @Value("${desperado.match.bot-think-time:1500ms}") Duration botThinkTime,
                        @Value("${desperado.match.bot-cooldown:4s}") Duration botCooldown) {
        this.messaging = messaging;
        this.events = events;
        this.clock = clock;
        this.botStrategy = botStrategy;
        this.matchLength = matchLength;
        this.moveTimeout = moveTimeout;
        this.botThinkTime = botThinkTime;
        this.botCooldown = botCooldown;
    }

    @PostConstruct
    void startTicking() {
        scheduler.scheduleAtFixedRate(this::tickAll, 250, 250, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stopTicking() {
        scheduler.shutdownNow();
    }

    public MatchSnapshot startMatch(String roomCode, List<Seat> seats) {
        Match match = Match.start(roomCode, seats, clock.instant(), matchLength, moveTimeout);
        Match existing = matches.putIfAbsent(roomCode, match);
        if (existing != null && existing.getStatus() == Match.Status.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A match is already running in this room");
        }
        if (existing != null) {
            matches.put(roomCode, match);
        }
        MatchSnapshot snapshot = match.snapshot();
        broadcast(snapshot);
        return snapshot;
    }

    public MatchSnapshot getSnapshot(String roomCode) {
        return findMatch(roomCode).snapshot();
    }

    //so the ai coach agent can view the match records and do its analysis
    public MatchRecord getRecord(String roomCode) {
        return findMatch(roomCode).record();
    }

    public MoveResult makeMove(String roomCode, long playerId, int boardId, String uci) {
        Match match = findMatch(roomCode);
        if (match.isBotSeat(playerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bots move on their own");
        }
        MoveResult result = match.makeMove(playerId, boardId, uci, clock.instant());
        broadcast(match.snapshot());
        return result;
    }

    // ---------- clock ----------

    void tickAll() {
        for (Match match : matches.values()) {
            try {
                Instant now = clock.instant();
                boolean changed = match.tick(now) | match.playBots(now, botStrategy, botThinkTime, botCooldown);

                // true exactly once per match, however it finished (a tick, or a late move after the end time).
                //publish BEFORE broadcasting, so listeners (like room reset, coach "analysing" state) are ready
                //by the time clients see FINISHED.
                boolean justFinished = match.claimFinishAnnouncement();
                if (justFinished) {
                    events.publishEvent(new MatchFinishedEvent(match.getRoomCode()));
                }
                if (changed || justFinished) {
                    broadcast(match.snapshot());
                }
            } catch (Exception e) {
                log.error("Tick failed for match {}", match.getRoomCode(), e);
            }
        }
    }

    private Match findMatch(String roomCode) {
        Match match = matches.get(roomCode.toUpperCase());
        if (match == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No match in this room");
        }
        return match;
    }

    private void broadcast(MatchSnapshot snapshot) {
        messaging.convertAndSend("/topic/matches/" + snapshot.roomCode(), snapshot);
    }
}
