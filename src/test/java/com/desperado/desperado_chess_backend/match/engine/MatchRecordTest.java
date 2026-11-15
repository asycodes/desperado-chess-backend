package com.desperado.desperado_chess_backend.match.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchRecordTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration ONE_MINUTE = Duration.ofSeconds(60);
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final Seat alice = new Seat(1L, "alice", 1, false);
    private final Seat bob = new Seat(2L, "bob", 2, false);

    private Match twoPlayerMatch() {
        return Match.start("ABCD", List.of(alice, bob), T0, TEN_MINUTES, ONE_MINUTE);
    }

    private MatchSnapshot.BoardState onlyBoard(Match match) {
        return match.snapshot().boards().get(0);
    }

    @Test
    void moves_areRecordedWithPositionThinkTimeAndPoints() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);

        match.makeMove(board.whiteId(), board.id(), "e2e4", T0.plusSeconds(3));
        match.makeMove(board.blackId(), board.id(), "d7d5", T0.plusSeconds(5));
        match.makeMove(board.whiteId(), board.id(), "e4d5", T0.plusSeconds(9)); // takes a pawn

        GameRecord game = match.record().games().get(0);
        assertThat(game.result()).isEqualTo(GameRecord.Result.UNFINISHED);
        assertThat(game.events()).hasSize(3);

        BoardEvent first = game.events().get(0);
        assertThat(first.type()).isEqualTo(BoardEvent.Type.MOVE);
        assertThat(first.fenBefore()).isEqualTo(START_FEN);
        assertThat(first.uci()).isEqualTo("e2e4");
        assertThat(first.thinkMs()).isEqualTo(3_000);

        BoardEvent capture = game.events().get(2);
        assertThat(capture.captured()).isEqualTo("PAWN");
        assertThat(capture.points()).isEqualTo(1);
        assertThat(capture.thinkMs()).isEqualTo(4_000); // since black's move at 5 s
    }

    @Test
    void checkmate_closesTheGameWithItsResult_andTheNextGameIsRecordedSeparately() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);
        long white = board.whiteId();
        long black = board.blackId();

        // Fool's mate: black wins
        match.makeMove(white, board.id(), "f2f3", T0.plusSeconds(1));
        match.makeMove(black, board.id(), "e7e5", T0.plusSeconds(2));
        match.makeMove(white, board.id(), "g2g4", T0.plusSeconds(3));
        match.makeMove(black, board.id(), "d8h4", T0.plusSeconds(4));
        // colours swapped: the previous black now has White
        match.makeMove(black, board.id(), "e2e4", T0.plusSeconds(5));

        List<GameRecord> games = match.record().games();
        assertThat(games).hasSize(2);

        GameRecord first = games.get(0);
        assertThat(first.gameNumber()).isEqualTo(1);
        assertThat(first.whiteId()).isEqualTo(white);
        assertThat(first.result()).isEqualTo(GameRecord.Result.BLACK_WON);
        assertThat(first.events()).hasSize(4);
        assertThat(first.events().get(3).checkmate()).isTrue();

        GameRecord second = games.get(1);
        assertThat(second.gameNumber()).isEqualTo(2);
        assertThat(second.whiteId()).isEqualTo(black);
        assertThat(second.result()).isEqualTo(GameRecord.Result.UNFINISHED);
        assertThat(second.events()).hasSize(1);
    }

    @Test
    void timeout_isRecordedAsAPassWithoutAMove() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);

        match.tick(T0.plusSeconds(61)); // white never moved

        BoardEvent event = match.record().games().get(0).events().get(0);
        assertThat(event.type()).isEqualTo(BoardEvent.Type.TIMEOUT_PASS);
        assertThat(event.playerId()).isEqualTo(board.whiteId());
        assertThat(event.uci()).isNull();
        assertThat(event.fenBefore()).isEqualTo(START_FEN);
    }

    @Test
    void finishAnnouncement_isClaimedExactlyOnce_evenWhenALateMoveFinishedTheMatch() {
        Match match = twoPlayerMatch();
        MatchSnapshot.BoardState board = onlyBoard(match);
        assertThat(match.claimFinishAnnouncement()).isFalse(); // still running

        try {
            match.makeMove(board.whiteId(), board.id(), "e2e4", T0.plus(TEN_MINUTES)); // arrives at the end time
        } catch (MatchRuleException expected) {
            // MATCH_OVER, and the match is now FINISHED without a tick
        }

        assertThat(match.claimFinishAnnouncement()).isTrue();
        assertThat(match.claimFinishAnnouncement()).isFalse();
    }

    @Test
    void record_containsPlayersWithFinalScores() {
        Match match = twoPlayerMatch();
        MatchRecord record = match.record();

        assertThat(record.matchId()).isNotBlank();
        assertThat(record.roomCode()).isEqualTo("ABCD");
        assertThat(record.startedAtMs()).isEqualTo(T0.toEpochMilli());
        assertThat(record.endedAtMs()).isEqualTo(T0.plus(TEN_MINUTES).toEpochMilli());
        assertThat(record.players()).extracting(MatchRecord.PlayerRecord::name).containsExactly("alice", "bob");
        assertThat(record.games()).isEmpty(); // nothing played yet
    }
}
