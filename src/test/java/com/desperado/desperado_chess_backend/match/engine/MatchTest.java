package com.desperado.desperado_chess_backend.match.engine;

import org.junit.jupiter.api.Test;

import com.desperado.desperado_chess_backend.match.engine.bot.BotStrategy;
import com.desperado.desperado_chess_backend.match.engine.bot.GreedyBot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration ONE_MINUTE = Duration.ofSeconds(60);

    private final Seat alice = new Seat(1L, "alice", 1, false);
    private final Seat bob = new Seat(2L, "bob", 2, false);
    private final Seat cara = new Seat(3L, "cara", 3, false);
    private final Seat bot = new Seat(4L, "Bot 1", 4, true);

    private Match twoPlayerMatch() {
        return Match.start("ABCD", List.of(alice, bob), T0, TEN_MINUTES, ONE_MINUTE);
    }

    private MatchSnapshot.BoardState onlyBoard(Match match) {
        return match.snapshot().boards().get(0);
    }

    private Instant at(int seconds) {
        return T0.plusSeconds(seconds);
    }

    @Test
    void start_withFourPlayers_createsSixBoardsAndEachPlayerIsOnThree() {
        Match match = Match.start("ABCD", List.of(alice, bob, cara, bot), T0, TEN_MINUTES, ONE_MINUTE);
        List<MatchSnapshot.BoardState> boards = match.snapshot().boards();

        assertThat(boards).hasSize(6);
        for (long id : List.of(1L, 2L, 3L, 4L)) {
            long count = boards.stream().filter(b -> b.whiteId() == id || b.blackId() == id).count();
            long asWhite = boards.stream().filter(b -> b.whiteId() == id).count();
            assertThat(count).isEqualTo(3);
            assertThat(asWhite).isBetween(1L, 2L);
        }
    }

    @Test
    void makeMove_legalMove_passesTheTurnToTheOpponent() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);

        match.makeMove(board.whiteId(), board.id(), "e2e4", at(5));

        assertThat(onlyBoard(match).playerToMove()).isEqualTo(board.blackId());
    }

    @Test
    void makeMove_whenNotYourTurn_isRejected() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);

        assertThatThrownBy(() -> match.makeMove(board.blackId(), board.id(), "e7e5", at(5)))
                .isInstanceOf(MatchRuleException.class)
                .extracting(e -> ((MatchRuleException) e).getReason())
                .isEqualTo(MatchRuleException.Reason.NOT_YOUR_TURN);
    }

    @Test
    void makeMove_illegalMove_isRejected() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);

        assertThatThrownBy(() -> match.makeMove(board.whiteId(), board.id(), "e2e5", at(5)))
                .isInstanceOf(MatchRuleException.class)
                .extracting(e -> ((MatchRuleException) e).getReason())
                .isEqualTo(MatchRuleException.Reason.ILLEGAL_MOVE);
    }

    @Test
    void capture_awardsThePieceValue() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);

        match.makeMove(board.whiteId(), board.id(), "e2e4", at(1));
        match.makeMove(board.blackId(), board.id(), "d7d5", at(2));
        MoveResult result = match.makeMove(board.whiteId(), board.id(), "e4d5", at(3)); // takes a pawn

        assertThat(result.pointsAwarded()).isEqualTo(1);
        assertThat(result.bountyApplied()).isFalse();
    }

    @Test
    void capturingFromTheLeader_scoresDouble() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);
        match.addScore(board.blackId(), 10); // black is clearly the leader

        match.makeMove(board.whiteId(), board.id(), "e2e4", at(1));
        match.makeMove(board.blackId(), board.id(), "d7d5", at(2));
        MoveResult result = match.makeMove(board.whiteId(), board.id(), "e4d5", at(3));

        assertThat(result.pointsAwarded()).isEqualTo(2);
        assertThat(result.bountyApplied()).isTrue();
    }

    @Test
    void checkmate_awardsTheBonusAndRestartsWithSwappedColours() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);
        long white = board.whiteId();
        long black = board.blackId();

        // Fool's mate: black mates in 2
        match.makeMove(white, board.id(), "f2f3", at(1));
        match.makeMove(black, board.id(), "e7e5", at(2));
        match.makeMove(white, board.id(), "g2g4", at(3));
        MoveResult result = match.makeMove(black, board.id(), "d8h4", at(4));

        assertThat(result.checkmate()).isTrue();
        assertThat(result.pointsAwarded()).isEqualTo(PieceValues.CHECKMATE_BONUS);

        MatchSnapshot.BoardState after = onlyBoard(match);
        assertThat(after.whiteId()).isEqualTo(black);
        assertThat(after.blackId()).isEqualTo(white);
        assertThat(after.fen()).startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w");
    }

    @Test
    void moveTimeout_passesTheTurn() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);

        boolean changed = match.tick(at(61)); // white never moved

        assertThat(changed).isTrue();
        assertThat(onlyBoard(match).playerToMove()).isEqualTo(board.blackId());
    }

    @Test
    void moveTimeout_whileInCheck_countsAsCheckmate() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);
        long white = board.whiteId();
        long black = board.blackId();

        match.makeMove(white, board.id(), "e2e4", at(1));
        match.makeMove(black, board.id(), "f7f6", at(2));
        match.makeMove(white, board.id(), "d1h5", at(3)); // check

        match.tick(at(3 + 61)); // black doesn't respond in time

        int whiteScore = match.snapshot().seats().stream()
                .filter(s -> s.playerId() == white).findFirst().orElseThrow().score();
        assertThat(whiteScore).isEqualTo(PieceValues.CHECKMATE_BONUS);
    }

    @Test
    void makeMove_afterTheMatchEnds_isRejected() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);

        assertThatThrownBy(() -> match.makeMove(board.whiteId(), board.id(), "e2e4", T0.plus(TEN_MINUTES)))
                .isInstanceOf(MatchRuleException.class)
                .extracting(e -> ((MatchRuleException) e).getReason())
                .isEqualTo(MatchRuleException.Reason.MATCH_OVER);
        assertThat(match.getStatus()).isEqualTo(Match.Status.FINISHED);
    }
        private static final Duration THINK = Duration.ofMillis(1500);
    private static final Duration COOLDOWN = Duration.ofSeconds(4);

    @Test
    void playBots_waitsForThinkTimeThenMoves() {
        Seat human = new Seat(1L, "alice", 1, false);
        Seat botSeat = new Seat(2L, "Bot 1", 2, true);
        Match match = Match.start("ABCD", List.of(human, botSeat), T0, TEN_MINUTES, ONE_MINUTE);
        assertThat(onlyBoard(match).playerToMove()).isEqualTo(2L); // the bot has White here

        BotStrategy bot = new GreedyBot(new java.util.Random(1));

        assertThat(match.playBots(T0.plusMillis(500), bot, THINK, COOLDOWN)).isFalse();  // still "thinking"
        assertThat(match.playBots(T0.plusMillis(2000), bot, THINK, COOLDOWN)).isTrue();  // moved
        assertThat(onlyBoard(match).playerToMove()).isEqualTo(1L);                        // human's turn now
    }

    @Test
    void playBots_cooldownStopsABotFromMovingOnTwoBoardsAtOnce() {
        Seat ann = new Seat(1L, "ann", 1, false);
        Seat ben = new Seat(2L, "ben", 2, false);
        Seat botSeat = new Seat(3L, "Bot 1", 3, true);
        Match match = Match.start("ABCD", List.of(ann, ben, botSeat), T0, TEN_MINUTES, ONE_MINUTE);
        BotStrategy bot = new GreedyBot(new java.util.Random(1));

        // Make the bot "to move" on every board it plays: if a human has White against it, play one move.
        for (MatchSnapshot.BoardState b : match.snapshot().boards()) {
            boolean botOnBoard = b.whiteId() == 3L || b.blackId() == 3L;
            if (botOnBoard && b.playerToMove() != 3L) {
                match.makeMove(b.playerToMove(), b.id(), "e2e4", T0);
            }
        }
        assertThat(botTurns(match)).isEqualTo(2);

        match.playBots(T0.plusSeconds(2), bot, THINK, COOLDOWN);
        assertThat(botTurns(match)).isEqualTo(1); // moved on one board, cooldown blocks the other

        match.playBots(T0.plusSeconds(3), bot, THINK, COOLDOWN);
        assertThat(botTurns(match)).isEqualTo(1); // still cooling down

        match.playBots(T0.plusSeconds(2).plus(COOLDOWN), bot, THINK, COOLDOWN);
        assertThat(botTurns(match)).isZero();     // cooldown over, second board played
    }

    private long botTurns(Match match) {
        return match.snapshot().boards().stream().filter(b -> b.playerToMove() == 3L).count();
    }
}