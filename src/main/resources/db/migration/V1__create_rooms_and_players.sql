CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code CHAR(4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mode VARCHAR(24) NOT NULL DEFAULT 'FFA',
    active BOOL NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_rooms_code UNIQUE (code)
);

CREATE TABLE players (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    position INT NOT NULL,
    name VARCHAR(24) NOT NULL,
    CONSTRAINT uk_player_room_position UNIQUE (room_id, position),
    CONSTRAINT uk_player_room_name UNIQUE (room_id, name),
    CONSTRAINT fk_player_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT chk_player_position CHECK (position BETWEEN 1 AND 4)
);