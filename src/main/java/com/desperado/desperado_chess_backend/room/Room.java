package com.desperado.desperado_chess_backend.room;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "char(4)")
    private String code;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 24)
    private String mode = "FFA";

    @Column(nullable = false)
    private boolean active = false;

    @OneToMany(mappedBy = "room")
    @OrderBy("position ASC")
    private List<Player> players = new ArrayList<>();

    public Room(String code) {
        this.code = code;
    }
}