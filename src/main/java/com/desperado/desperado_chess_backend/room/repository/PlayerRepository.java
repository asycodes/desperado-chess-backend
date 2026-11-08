package com.desperado.desperado_chess_backend.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.desperado.desperado_chess_backend.room.entity.Player;
import com.desperado.desperado_chess_backend.room.entity.Room;

import java.util.List;


public interface PlayerRepository extends JpaRepository<Player, Long> {
    boolean existsByRoomAndNameIgnoreCase(Room room, String name);
    List<Player> findByRoomOrderByPositionAsc(Room room);
}