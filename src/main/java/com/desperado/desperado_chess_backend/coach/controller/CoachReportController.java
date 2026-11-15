package com.desperado.desperado_chess_backend.coach;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class CoachReportController {

    /** What the coach service sends back. {@code status} is "READY" or "FAILED". */
    public record Callback(String matchId, String status, JsonNode report) {}

    private final WebhookSigner signer;
    private final CoachReportService reports;
    private final JsonMapper json;

    public CoachReportController(WebhookSigner signer, CoachReportService reports, JsonMapper json) {
        this.signer = signer;
        this.reports = reports;
        this.json = json;
    }

    @PostMapping("/internal/coach/reports/{roomCode}")
    public ResponseEntity<Void> receive(@PathVariable String roomCode,
                                        @RequestHeader(value = WebhookSigner.TIMESTAMP_HEADER, required = false) String timestamp,
                                        @RequestHeader(value = WebhookSigner.SIGNATURE_HEADER, required = false) String signature,
                                        @RequestBody String body) {
        if (!signer.verify(timestamp, signature, body)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Callback callback;
        try {
            callback = json.readValue(body, Callback.class);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().build();
        }
        if (callback.matchId() == null || callback.status() == null) {
            return ResponseEntity.badRequest().build();
        }

        String code = roomCode.toUpperCase();
        Optional<CoachReportService.View> applied = switch (callback.status()) {
            case "READY" -> callback.report() == null
                    ? Optional.empty()
                    : reports.complete(code, callback.matchId(), callback.report());
            case "FAILED" -> reports.fail(code, callback.matchId());
            default -> Optional.empty();
        };
        return applied.isPresent()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    /** For a results screen that loads after the report was pushed (e.g. a page refresh). */
    @GetMapping("/matches/{roomCode}/report")
    public CoachReportService.View get(@PathVariable String roomCode) {
        return reports.get(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No report for this room"));
    }
}
