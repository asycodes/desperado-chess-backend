package com.desperado.desperado_chess_backend.match.engine;

import com.desperado.desperado_chess_backend.match.engine.bot.BotStrategy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Match {

    public enum Status { RUNNING, FINISHED }

    private final String matchId = UUID.randomUUID().toString();
    private final String roomCode;
    private final List<Seat> seats;
    private final List<BoardGame> boards;
    private final Map<Long, Integer> scores = new LinkedHashMap<>();
    private final Map<Long, Instant> botLastMoveAt = new HashMap<>();
    private final Instant startedAt;
    private final Instant endsAt;
    private final Duration moveTimeout;
    private Status status = Status.RUNNING;
    private boolean finishAnnounced = false;

    private Match(String roomCode, List<Seat> seats, List<BoardGame> boards,
                  Instant startedAt, Instant endsAt, Duration moveTimeout) {
        this.roomCode = roomCode;
        this.seats = List.copyOf(seats);
        this.boards = boards;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.moveTimeout = moveTimeout;
        seats.forEach(s -> scores.put(s.playerId(), 0));
    }

    public boolean isBotSeat(long playerId) {
        return seats.stream().anyMatch(s -> s.playerId() == playerId && s.bot());
    }

    //create board each player, round robin style
    public static Match start(String roomCode, List<Seat> seats, Instant now,
                              Duration matchLength, Duration moveTimeout) {
        if (seats.size() < 2 || seats.size() > 4) {
            throw new IllegalArgumentException("A match needs 2 to 4 seats");
        }
        List<BoardGame> boards = new ArrayList<>();
        int nextId = 1;
        for (int i = 0; i < seats.size(); i++) {
            for (int j = i + 1; j < seats.size(); j++) {
                // alternate who gets White so colours are balanced across each player's boards
                Seat white = (i + j) % 2 == 0 ? seats.get(i) : seats.get(j);
                Seat black = white == seats.get(i) ? seats.get(j) : seats.get(i);
                boards.add(new BoardGame(nextId++, white.playerId(), black.playerId(), now));
            }
        }
        return new Match(roomCode, seats, boards, now, now.plus(matchLength), moveTimeout);
    }

    //here are player related actions

    public synchronized MoveResult makeMove(long playerId, int boardId, String uci, Instant now) {
        if (finishIfDue(now) || status == Status.FINISHED) {
            throw new MatchRuleException(MatchRuleException.Reason.MATCH_OVER, "The match is over");
        }
        BoardGame board = findBoard(boardId);
        if (!board.involves(playerId)) {
            throw new MatchRuleException(MatchRuleException.Reason.NOT_YOUR_BOARD, "You are not on this board");
        }
        if (board.playerToMove() != playerId) {
            throw new MatchRuleException(MatchRuleException.Reason.NOT_YOUR_TURN, "It is not your turn on this board");
        }

        long opponent = board.opponentOf(playerId);
        Long leader = leaderId(); //decided before the move changes any score
        String fenBefore = board.getFen();
        long thinkMs = board.thinkMs(now);
        boolean moverIsWhite = playerId == board.getWhiteId();

        MoveOutcome outcome = board.play(uci, now);

        int points = 0;
        boolean bounty = false;
        if (outcome.captured() != null) {
            points = PieceValues.of(outcome.captured());
            if (leader != null && leader == opponent) {
                points *= 2;
                bounty = true;
            }
        }
        if (outcome.checkmate()) {
            points += PieceValues.CHECKMATE_BONUS;
        }
        if (points > 0) {
            addScore(playerId, points);
            board.addPoints(playerId, points);
        }

        board.record(BoardEvent.move(playerId, fenBefore, uci.toLowerCase(), thinkMs,
                outcome.captured() == null ? null : outcome.captured().name(),
                points, bounty, outcome.checkmate(), now.toEpochMilli()));

        if (outcome.checkmate()) {
            board.restartWithSwappedColours(now,
                    moverIsWhite ? GameRecord.Result.WHITE_WON : GameRecord.Result.BLACK_WON);
        } else if (outcome.drawn()) {
            board.restartWithSwappedColours(now, GameRecord.Result.DRAW);
        }
        return new MoveResult(points, bounty, outcome.checkmate());
    }

    /**
     * Called regularly by a scheduler. Handles move timeouts and the end of the match.
     * Returns true if anything changed (so the caller knows to broadcast).
     */
    public synchronized boolean tick(Instant now) {
        if (status == Status.FINISHED) return false;
        if (finishIfDue(now)) return true;

        boolean changed = false;
        for (BoardGame board : boards) {
            if (!board.isTimedOut(now, moveTimeout)) continue;

            long stalledPlayer = board.playerToMove();
            String fenBefore = board.getFen();
            long thinkMs = board.thinkMs(now);

            if (board.sideToMoveInCheck()) {
                long winner = board.opponentOf(stalledPlayer);
                boolean winnerIsWhite = winner == board.getWhiteId();
                addScore(winner, PieceValues.CHECKMATE_BONUS);
                board.addPoints(winner, PieceValues.CHECKMATE_BONUS);
                board.record(BoardEvent.timeout(BoardEvent.Type.TIMEOUT_MATE, stalledPlayer, fenBefore,
                        thinkMs, now.toEpochMilli()));
                board.restartWithSwappedColours(now,
                        winnerIsWhite ? GameRecord.Result.WHITE_WON : GameRecord.Result.BLACK_WON);
            } else {
                board.record(BoardEvent.timeout(BoardEvent.Type.TIMEOUT_PASS, stalledPlayer, fenBefore,
                        thinkMs, now.toEpochMilli()));
                board.passTurn(now);
            }
            changed = true;
        }
        return changed;
    }

    /**
     * Plays bot turns at a human-like pace: each bot waits `thinkTime` after its turn starts on a board,
     * and makes at most one move per `cooldown` across ALL its boards (like a person switching boards).
     */
    public synchronized boolean playBots(Instant now, BotStrategy bot, Duration thinkTime, Duration cooldown) {
        if (status != Status.RUNNING || !now.isBefore(endsAt)) return false;

        boolean changed = false;
        List<BoardGame> longestWaitingFirst = boards.stream()
                .sorted(Comparator.comparing(BoardGame::getTurnStartedAt))
                .toList();

        for (BoardGame board : longestWaitingFirst) {
            long toMove = board.playerToMove();
            if (!isBotSeat(toMove)) continue;
            if (now.isBefore(board.getTurnStartedAt().plus(thinkTime))) continue;

            Instant last = botLastMoveAt.get(toMove);
            if (last != null && now.isBefore(last.plus(cooldown))) continue; // this bot is "busy"

            String uci = bot.chooseMove(board.getFen());
            makeMove(toMove, board.getId(), uci, now);
            botLastMoveAt.put(toMove, now);
            changed = true;
        }
        return changed;
    }

    /**
     * True exactly once, the first time it's called after the match has finished, whichever path
     * finished it (a tick, or a late move that hit the end time). Lets the caller announce the end once.
     */
    public synchronized boolean claimFinishAnnouncement() {
        if (status == Status.FINISHED && !finishAnnounced) {
            finishAnnounced = true;
            return true;
        }
        return false;
    }


    public synchronized MatchSnapshot snapshot() {
        Long leader = leaderId();
        List<MatchSnapshot.SeatScore> seatScores = seats.stream()
                .map(s -> new MatchSnapshot.SeatScore(s.playerId(), s.name(), s.position(), s.bot(),
                        scores.get(s.playerId()), leader != null && leader == s.playerId()))
                .toList();
        List<MatchSnapshot.BoardState> boardStates = boards.stream()
                .map(b -> new MatchSnapshot.BoardState(b.getId(), b.getWhiteId(), b.getBlackId(), b.getFen(),
                        b.playerToMove(), b.getTurnStartedAt().plus(moveTimeout).toEpochMilli(),
                        b.getPointsByPlayer()))
                .toList();
        return new MatchSnapshot(roomCode, status.name(), endsAt.toEpochMilli(), seatScores, boardStates);
    }

    /** The full history of the match, for post-match analysis. */
    public synchronized MatchRecord record() {
        List<MatchRecord.PlayerRecord> players = seats.stream()
                .map(s -> new MatchRecord.PlayerRecord(s.playerId(), s.name(), s.position(), s.bot(),
                        scores.get(s.playerId())))
                .toList();
        List<GameRecord> games = boards.stream()
                .flatMap(b -> b.history().stream())
                .toList();
        return new MatchRecord(matchId, roomCode, startedAt.toEpochMilli(), endsAt.toEpochMilli(), players, games);
    }

    public synchronized Status getStatus() { return status; }
    public String getRoomCode() { return roomCode; }
    public String getMatchId() { return matchId; }

    // ---------- internals ----------

    /** The single top scorer, or null when nobody has points or there is a tie for first. */
    Long leaderId() {
        int best = 0;
        Long leader = null;
        boolean tie = false;
        for (Map.Entry<Long, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                leader = entry.getKey();
                tie = false;
            } else if (entry.getValue() == best && best > 0) {
                tie = true;
            }
        }
        return tie ? null : leader;
    }

    void addScore(long playerId, int points) {
        scores.merge(playerId, points, Integer::sum);
    }

    private boolean finishIfDue(Instant now) {
        if (status == Status.RUNNING && !now.isBefore(endsAt)) {
            status = Status.FINISHED;
            return true;
        }
        return false;
    }

    private BoardGame findBoard(int boardId) {
        return boards.stream()
                .filter(b -> b.getId() == boardId)
                .findFirst()
                .orElseThrow(() -> new MatchRuleException(MatchRuleException.Reason.BOARD_NOT_FOUND, "No board " + boardId));
    }
}
