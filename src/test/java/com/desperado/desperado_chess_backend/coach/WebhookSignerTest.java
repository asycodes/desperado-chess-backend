package com.desperado.desperado_chess_backend.coach;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private static final Instant NOW = Instant.parse("2026-11-15T08:00:00Z");
    private static final String BODY = "{\"eventId\":\"m-1\",\"type\":\"match.finished\"}";

    private final WebhookSigner signer = new WebhookSigner(
            new CoachProperties(true, "http://localhost:8000", "test-secret", Duration.ofMinutes(5)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final long now = NOW.getEpochSecond();

    @Test
    void validSignature_isAccepted() {
        String signature = signer.sign(now, BODY);
        assertThat(signature).startsWith("sha256=");
        assertThat(signer.verify(Long.toString(now), signature, BODY)).isTrue();
    }

    @Test
    void changedBody_isRejected() {
        String signature = signer.sign(now, BODY);
        assertThat(signer.verify(Long.toString(now), signature, BODY.replace("m-1", "m-2"))).isFalse();
    }

    @Test
    void wrongSecret_isRejected() {
        WebhookSigner other = new WebhookSigner(
                new CoachProperties(true, "http://localhost:8000", "another-secret", Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(signer.verify(Long.toString(now), other.sign(now, BODY), BODY)).isFalse();
    }

    @Test
    void oldTimestamp_isRejected_evenWithACorrectSignature() {
        long tenMinutesAgo = now - 600;
        assertThat(signer.verify(Long.toString(tenMinutesAgo), signer.sign(tenMinutesAgo, BODY), BODY)).isFalse();
    }

    @Test
    void missingOrMalformedHeaders_areRejected() {
        String signature = signer.sign(now, BODY);
        assertThat(signer.verify(null, signature, BODY)).isFalse();
        assertThat(signer.verify(Long.toString(now), null, BODY)).isFalse();
        assertThat(signer.verify("not-a-number", signature, BODY)).isFalse();
    }
}
