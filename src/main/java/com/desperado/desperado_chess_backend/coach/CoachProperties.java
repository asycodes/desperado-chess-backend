package com.desperado.desperado_chess_backend.coach;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the AI coach service, bound from {@code desperado.coach.*} in application.yml.
 *
 * @param enabled  when false, finished matches are not sent anywhere and the results screen shows no awards
 * @param url      base URL of the coach service, e.g. http://localhost:8000
 * @param secret   shared secret for HMAC signatures, in both directions
 * @param maxSkew  how old a signed request may be before it is rejected (replay protection)
 */
@ConfigurationProperties(prefix = "desperado.coach")
public record CoachProperties(boolean enabled, String url, String secret, Duration maxSkew) {

    public CoachProperties {
        if (maxSkew == null) maxSkew = Duration.ofMinutes(5);
        if (enabled && (secret == null || secret.isBlank())) {
            throw new IllegalStateException("desperado.coach.secret must be set when the coach is enabled");
        }
    }
}
