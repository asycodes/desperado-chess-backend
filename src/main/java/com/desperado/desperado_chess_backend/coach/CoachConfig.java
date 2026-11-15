package com.desperado.desperado_chess_backend.coach;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CoachProperties.class)
public class CoachConfig {

    /** HTTP client for calling the coach service, with timeouts so a slow service can't hang a thread. */
    @Bean
    RestClient coachRestClient(CoachProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl(properties.url() == null ? "http://localhost:8000" : properties.url())
                .requestFactory(factory)
                .build();
    }
}
