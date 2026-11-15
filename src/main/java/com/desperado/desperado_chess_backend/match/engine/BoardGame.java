package com.desperado.desperado_chess_backend.match.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BoardGame {

    private final int id;
    private long whiteId;
    private long blackId;
    private Board board = new Board();
    private Instant turnStartedAt;
    private final Map<Long, Integer> pointsByPlayer = new HashMap<>();

    private final List<GameRecord> finishedGames = new ArrayList<>();
    private List<BoardEvent> currentEvents = new ArrayList<>();
    private int gameNumber = 1;

    BoardGame(int id, long whiteId, long blackId, Instant now) {
        this.id = id;
        this.whiteId = whiteId;
        this.blackId = blackId;
        this.turnStartedAt = now;
    }

    public int getId() { return id; }
    public long getWhiteId() { return whiteId; }
    public long getBlackId() { return blackId; }
    public String getFen() { return board.getFen(); }
    public Instant getTurnStartedAt() { return turnStartedAt; }
    public Map<Long, Integer> getPointsByPlayer() { return Map.copyOf(pointsByPlayer); }

    public boolean involves(long playerId) {
        return playerId == whiteId || playerId == blackId;
    }

    public long opponentOf(long playerId) {
        return playerId == whiteId ? blackId : whiteId;
    }

    public long playerToMove() {
        return board.getSideToMove() == Side.WHITE ? whiteId : blackId;
    }

    boolean isTimedOut(Instant now, Duration moveTimeout) {
        return !now.isBefore(turnStartedAt.plus(moveTimeout));
    }

    boolean sideToMoveInCheck() {
        return board.isKingAttacked();
    }

    /** Milliseconds since the current turn started. */
    long thinkMs(Instant now) {
        return Duration.between(turnStartedAt, now).toMillis();
    }

    /** Validates and plays a move in UCI notation (e.g. "e2e4", "e7e8q"). */
    MoveOutcome play(String uci, Instant now) {
        Side side = board.getSideToMove();
        Move move;
        try {
            move = new Move(uci.toLowerCase(), side);
        } catch (RuntimeException e) {
            throw new MatchRuleException(MatchRuleException.Reason.ILLEGAL_MOVE, "Not a valid move: " + uci);
        }
        if (!board.legalMoves().contains(move)) {
            throw new MatchRuleException(MatchRuleException.Reason.ILLEGAL_MOVE, "Illegal move: " + uci);
        }

        Piece moving = board.getPiece(move.getFrom());
        Piece target = board.getPiece(move.getTo());
        PieceType captured = null;
        if (target != Piece.NONE) {
            captured = target.getPieceType();
        } else if (moving.getPieceType() == PieceType.PAWN && move.getFrom().getFile() != move.getTo().getFile()) {
            captured = PieceType.PAWN; // en passant
        }

        board.doMove(move);
        turnStartedAt = now;

        boolean checkmate = board.isMated();
        boolean drawn = !checkmate && (board.isStaleMate() || board.isDraw());
        return new MoveOutcome(captured, checkmate, drawn);
    }

    /** The player to move ran out of time: the turn goes to the opponent without a move. */
    void passTurn(Instant now) {
        String[] fen = board.getFen().split(" ");
        fen[1] = fen[1].equals("w") ? "b" : "w";
        fen[3] = "-";
        board.loadFromFen(String.join(" ", fen));
        turnStartedAt = now;
    }

    /**
     * After a checkmate or draw: closes the current game in the history with its result,
     * then starts a fresh game between the same two players with colours swapped.
     */
    void restartWithSwappedColours(Instant now, GameRecord.Result result) {
        finishedGames.add(new GameRecord(id, gameNumber, whiteId, blackId, result, List.copyOf(currentEvents)));
        currentEvents = new ArrayList<>();
        gameNumber++;

        long previousWhite = whiteId;
        whiteId = blackId;
        blackId = previousWhite;
        board = new Board();
        turnStartedAt = now;
    }

    void addPoints(long playerId, int points) {
        pointsByPlayer.merge(playerId, points, Integer::sum);
    }

    void record(BoardEvent event) {
        currentEvents.add(event);
    }

    /** All games on this board, in order. The game still in progress is included as UNFINISHED. */
    List<GameRecord> history() {
        List<GameRecord> all = new ArrayList<>(finishedGames);
        if (!currentEvents.isEmpty()) {
            all.add(new GameRecord(id, gameNumber, whiteId, blackId,
                    GameRecord.Result.UNFINISHED, List.copyOf(currentEvents)));
        }
        return all;
    }
}
