package com.desperado.desperado_chess_backend.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.desperado.desperado_chess_backend.room.entity.Room;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByCode(String code);
    boolean existsByCode(String code);
}