package com.desperado.desperado_chess_backend.room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long>{
    Optional<Room> findByCode(String code);
    boolean existsByCode(String code);
}

