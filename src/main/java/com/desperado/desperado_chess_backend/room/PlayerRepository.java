package com.desperado.desperado_chess_backend.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface PlayerRepository extends JpaRepository<Player, Long> {
    boolean existsByRoomAndNameIgnoreCase(Room room, String name);
    List<Player> findByRoomOrderByPositionAsc(Room room);
}