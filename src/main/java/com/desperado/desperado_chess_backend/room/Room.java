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
@Table(name="rooms")
@Getter 
@Setter 
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Room {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "char(4)")
    private Integer code;

    @Column(name = "created_at", nullable= false, insertable= false, updatable = false)
    private LocalDateTime createAt;

    @Column(nullable = false, length = 24)
    private String mode;
    

    public Room(Integer code){
        this.code = code;
        this.mode = "FFA";

    }

}

