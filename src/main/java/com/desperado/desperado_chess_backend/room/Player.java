package com.desperado.desperado_chess_backend.room;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "players")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 24)
    private String name;

    @Column(name = "is_bot", nullable = false)
    private boolean isBot = false;

    public Player(Room room, String name, int position, boolean isBot) {
        this.room = room;
        this.name = name;
        this.position = position;
        this.isBot = isBot;
    }

    public boolean isHost() {
        return position == 1;
    }
}