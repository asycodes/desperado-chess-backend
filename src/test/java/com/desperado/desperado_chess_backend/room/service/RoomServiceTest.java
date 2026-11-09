package com.desperado.desperado_chess_backend.room.service;

import com.desperado.desperado_chess_backend.room.DTO.*;
import com.desperado.desperado_chess_backend.room.entity.Player;
import com.desperado.desperado_chess_backend.room.entity.Room;
import com.desperado.desperado_chess_backend.room.event.RoomChangedEvent;
import com.desperado.desperado_chess_backend.room.repository.PlayerRepository;
import com.desperado.desperado_chess_backend.room.repository.RoomRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private RoomService roomService;

    private Room room(long id, String code) {
        Room room = new Room(code);
        room.setId(id);
        return room;
    }

    private Player player(Room room, long id, String name, int position, boolean isBot) {
        Player player = new Player(room, name, position, isBot);
        ReflectionTestUtils.setField(player, "id", id);
        return player;
    }

    private void assertStatus(Throwable thrown, HttpStatus expected) {
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode()).isEqualTo(expected);
    }

    //for createRoom

    @Test
    void createRoom_savesHostInPositionOneAsHuman() {
        when(roomRepository.existsByCode(anyString())).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        JoinRoomResponse response = roomService.createRoom(new CreateRoomRequest("  kate  "));

        ArgumentCaptor<Player> saved = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("kate");      // trimmed
        assertThat(saved.getValue().getPosition()).isEqualTo(1);
        assertThat(saved.getValue().isBot()).isFalse();
        assertThat(response.code()).hasSize(4).matches("[A-Z]{4}");
    }

    //for joinRoom

    @Test
    void joinRoom_whenRoomIsFull_throwsConflict() {
        Room room = room(1L, "ABCD");
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomOrderByPositionAsc(room)).thenReturn(List.of(
                player(room, 1L, "a", 1, false),
                player(room, 2L, "b", 2, false),
                player(room, 3L, "c", 3, false),
                player(room, 4L, "d", 4, false)));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> roomService.joinRoom("ABCD", new JoinRoomRequest("e")));

        assertStatus(thrown, HttpStatus.CONFLICT);
        verify(playerRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void joinRoom_whenNameTaken_throwsConflict() {
        Room room = room(1L, "ABCD");
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomOrderByPositionAsc(room)).thenReturn(List.of(player(room, 1L, "kate", 1, false)));
        when(playerRepository.existsByRoomAndNameIgnoreCase(room, "Kate")).thenReturn(true);

        assertThatThrownBy(() -> roomService.joinRoom("ABCD", new JoinRoomRequest("Kate")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void joinRoom_whenMatchInProgress_throwsConflict() {
        Room room = room(1L, "ABCD");
        room.setActive(true);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> roomService.joinRoom("ABCD", new JoinRoomRequest("mei")));

        assertStatus(thrown, HttpStatus.CONFLICT);
    }

    @Test
    void joinRoom_fillsTheFirstFreeSeat() {
        Room room = room(1L, "ABCD");
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        // seats 1 and 3 taken, so seat 2 is free
        when(playerRepository.findByRoomOrderByPositionAsc(room)).thenReturn(List.of(
                player(room, 1L, "kate", 1, false),
                player(room, 3L, "ravi", 3, false)));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        roomService.joinRoom("ABCD", new JoinRoomRequest("mei"));

        ArgumentCaptor<Player> saved = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(saved.capture());
        assertThat(saved.getValue().getPosition()).isEqualTo(2);
        assertThat(saved.getValue().isBot()).isFalse();
        verify(eventPublisher).publishEvent(any(RoomChangedEvent.class));
    }

    @Test
    void joinRoom_whenRoomDoesNotExist_throwsNotFound() {
        when(roomRepository.findByCode("ZZZZ")).thenReturn(Optional.empty());

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> roomService.joinRoom("zzzz", new JoinRoomRequest("mei")));

        assertStatus(thrown, HttpStatus.NOT_FOUND);
    }

    //for addBot

    @Test
    void addBot_whenRequesterIsNotHost_throwsForbidden() {
        Room room = room(1L, "ABCD");
        Player guest = player(room, 2L, "mei", 2, false);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(guest));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> roomService.addBot("ABCD", new AddBotRequest(2L)));

        assertStatus(thrown, HttpStatus.FORBIDDEN);
        verify(playerRepository, never()).save(any());
    }

    @Test
    void addBot_whenRequesterIsInAnotherRoom_throwsForbidden() {
        Room room = room(1L, "ABCD");
        Room otherRoom = room(2L, "WXYZ");
        Player otherHost = player(otherRoom, 9L, "zed", 1, false);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findById(9L)).thenReturn(Optional.of(otherHost));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> roomService.addBot("ABCD", new AddBotRequest(9L)));

        assertStatus(thrown, HttpStatus.FORBIDDEN);
    }

    @Test
    void addBot_byHost_savesABotWithAFreeName() {
        Room room = room(1L, "ABCD");
        Player host = player(room, 1L, "kate", 1, false);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomOrderByPositionAsc(room)).thenReturn(List.of(host));
        when(playerRepository.existsByRoomAndNameIgnoreCase(room, "Bot 1")).thenReturn(false);
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        PlayerResponse bot = roomService.addBot("ABCD", new AddBotRequest(1L));

        assertThat(bot.nickname()).isEqualTo("Bot 1");
        assertThat(bot.isBot()).isTrue();
        assertThat(bot.isHost()).isFalse();
        assertThat(bot.position()).isEqualTo(2);
    }

    //for startMatch
    @Test
    void startMatch_withOnlyOnePlayer_throwsConflict() {
        Room room = room(1L, "ABCD");
        Player host = player(room, 1L, "kate", 1, false);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomOrderByPositionAsc(room)).thenReturn(List.of(host));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> roomService.startMatch("ABCD", new StartMatchRequest(1L, "FFA")));

        assertStatus(thrown, HttpStatus.CONFLICT);
        assertThat(room.isActive()).isFalse();
    }

    @Test
    void startMatch_byHostWithTwoPlayers_activatesRoomAndBroadcasts() {
        Room room = room(1L, "ABCD");
        Player host = player(room, 1L, "kate", 1, false);
        Player bot = player(room, 2L, "Bot 1", 2, true);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomOrderByPositionAsc(room)).thenReturn(List.of(host, bot));

        RoomResponse response = roomService.startMatch("ABCD", new StartMatchRequest(1L, "FFA"));

        assertThat(room.isActive()).isTrue();
        assertThat(response.active()).isTrue();
        verify(eventPublisher).publishEvent(any(RoomChangedEvent.class));
    }
}