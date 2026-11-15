package com.desperado.desperado_chess_backend.coach;

import com.desperado.desperado_chess_backend.coach.service.CoachReportService;
import com.desperado.desperado_chess_backend.match.engine.MatchRecord;
import com.desperado.desperado_chess_backend.match.event.MatchFinishedEvent;
import com.desperado.desperado_chess_backend.match.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * When a match finishes: marks its report as PENDING and sends the full match record to the coach service.
 *
 * <p>Runs on the scheduler thread that published the event, so it only does quick work here;
 * the HTTP call happens on {@link CoachClient}'s own thread.
 */
@Slf4j
@Component
public class CoachDispatcher {

    /** The webhook body. {@code eventId} lets the coach service ignore duplicate deliveries. */
    public record WebhookPayload(String eventId, String type, MatchRecord match) {}

    private final CoachProperties properties;
    private final MatchService matchService;
    private final CoachReportService reports;
    private final CoachClient client;
    private final JsonMapper json;

    public CoachDispatcher(CoachProperties properties, MatchService matchService,
                           CoachReportService reports, CoachClient client, JsonMapper json) {
        this.properties = properties;
        this.matchService = matchService;
        this.reports = reports;
        this.client = client;
        this.json = json;
    }

    @EventListener
    public void onMatchFinished(MatchFinishedEvent event) {
        if (!properties.enabled()) return;
        try {
            MatchRecord record = matchService.getRecord(event.roomCode());
            String body = json.writeValueAsString(new WebhookPayload(record.matchId(), "match.finished", record));

            reports.markPending(record.roomCode(), record.matchId());
            client.send(record.matchId(), body, () -> reports.fail(record.roomCode(), record.matchId()));
        } catch (Exception e) {
            log.error("Could not dispatch match {} to the coach", event.roomCode(), e);
        }
    }
}
