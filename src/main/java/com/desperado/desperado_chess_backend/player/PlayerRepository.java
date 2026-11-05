package com.desperado.desperado_chess_backend.player;

import org.springframework.data.jpa.repository.JpaRepository;
import com.desperado.desperado_chess_backend.room.Room;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    long countByShelf(Room room);
    boolean existsByShelfAndTitle(Room room, String name); 
}