package com.desperado.desperado_chess_backend.coach;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

/**
 * Signs and verifies requests between Desperado and the coach service.
 *
 * <p>Signature = HMAC-SHA256(secret, "{timestamp}.{raw body}"), sent as {@code sha256=<hex>}.
 * Including the timestamp means a captured request can't be replayed later, and signing the raw
 * body means any change to it breaks the signature.
 */
@Component
public class WebhookSigner {

    public static final String TIMESTAMP_HEADER = "X-Desperado-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Desperado-Signature";
    public static final String EVENT_ID_HEADER = "X-Desperado-Event-Id";

    private static final String ALGORITHM = "HmacSHA256";

    private final CoachProperties properties;
    private final Clock clock;

    public WebhookSigner(CoachProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String sign(long timestampSeconds, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal((timestampSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not compute webhook signature", e);
        }
    }

    /** True only if the signature matches the body and the timestamp is recent. */
    public boolean verify(String timestampHeader, String signatureHeader, String body) {
        if (timestampHeader == null || signatureHeader == null || body == null) return false;

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            return false;
        }
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - timestamp) > properties.maxSkew().toSeconds()) return false;

        byte[] expected = sign(timestamp, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signatureHeader.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual); // constant time: doesn't leak how many characters matched
    }
}
