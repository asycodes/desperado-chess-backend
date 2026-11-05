package com.desperado.desperado_chess_backend.player;

import com.desperado.desperado_chess_backend.room.Room;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(name="players")
@Getter 
@Setter 
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Player {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length=24)
    private String name;

    public Player(Room room, String name, Integer position){
        this.room = room;
        this.name = name;
        this.position = position;

    }

}
