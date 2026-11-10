package com.desperado.desperado_chess_backend.room.event;

import com.desperado.desperado_chess_backend.match.event.MatchFinishedEvent;
import com.desperado.desperado_chess_backend.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchFinishedListener {

    private final RoomService roomService;

    @EventListener
    public void onMatchFinished(MatchFinishedEvent event) {
        roomService.endMatch(event.roomCode());
    }
}