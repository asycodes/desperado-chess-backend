package com.desperado.desperado_chess_backend.coach;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends the "match finished" webhook to the coach service on its own background thread,
 * so a slow or sleeping coach service never delays the game.
 *
 * <p>Retries with exponential backoff (5 s, 10 s, 20 s, 40 s) on server errors, timeouts and refused
 * connections: long enough for a free-tier service to wake up. A 4xx answer means our request
 * is wrong, so it is not retried.
 */
@Slf4j
@Component
public class CoachClient {

    static final int MAX_ATTEMPTS = 5;
    static final long FIRST_DELAY_SECONDS = 5;

    private final RestClient rest;
    private final WebhookSigner signer;
    private final Clock clock;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "coach-webhook");
        thread.setDaemon(true);
        return thread;
    });

    public CoachClient(RestClient coachRestClient, WebhookSigner signer, Clock clock) {
        this.rest = coachRestClient;
        this.signer = signer;
        this.clock = clock;
    }

    /** Returns immediately. Calls {@code onGiveUp} if every attempt fails. */
    public void send(String eventId, String body, Runnable onGiveUp) {
        executor.execute(() -> attempt(eventId, body, 1, onGiveUp));
    }

    private void attempt(String eventId, String body, int attemptNumber, Runnable onGiveUp) {
        try {
            long timestamp = clock.instant().getEpochSecond();
            rest.post()
                    .uri("/webhooks/desperado")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebhookSigner.EVENT_ID_HEADER, eventId)
                    .header(WebhookSigner.TIMESTAMP_HEADER, Long.toString(timestamp))
                    .header(WebhookSigner.SIGNATURE_HEADER, signer.sign(timestamp, body))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Coach webhook {} delivered (attempt {})", eventId, attemptNumber);
        } catch (HttpClientErrorException e) {
            log.error("Coach webhook {} rejected with {}: not retrying", eventId, e.getStatusCode());
            onGiveUp.run();
        } catch (RestClientException e) {
            if (attemptNumber >= MAX_ATTEMPTS) {
                log.error("Coach webhook {} failed after {} attempts", eventId, attemptNumber, e);
                onGiveUp.run();
                return;
            }
            long delay = FIRST_DELAY_SECONDS << (attemptNumber - 1);
            log.warn("Coach webhook {} attempt {} failed ({}); retrying in {} s",
                    eventId, attemptNumber, e.getMessage(), delay);
            executor.schedule(() -> attempt(eventId, body, attemptNumber + 1, onGiveUp), delay, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
