package com.desperado.desperado_chess_backend.room.event;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;



@Component
@RequiredArgsConstructor
public class RoomBroadcaster {
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomChanged(RoomChangedEvent event) {
        messagingTemplate.convertAndSend("/topic/rooms/" + event.room().code(), event.room());
    }
}