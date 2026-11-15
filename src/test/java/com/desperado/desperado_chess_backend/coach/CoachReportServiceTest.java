package com.desperado.desperado_chess_backend.coach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.desperado.desperado_chess_backend.coach.service.CoachReportService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoachReportServiceTest {

    @Mock SimpMessagingTemplate messaging;
    @InjectMocks CoachReportService reports;

    private final JsonNode report = JsonMapper.builder().build().readTree("{\"awards\":[]}");

    @Test
    void report_forTheLatestMatch_isStoredAndPushed() {
        reports.markPending("ABCD", "match-2");

        assertThat(reports.complete("ABCD", "match-2", report)).isPresent();

        assertThat(reports.get("ABCD")).get()
                .extracting(CoachReportService.View::status)
                .isEqualTo(CoachReportService.Status.READY);
        verify(messaging).convertAndSend(eq("/topic/matches/ABCD/report"),
                eq(new CoachReportService.View("match-2", CoachReportService.Status.READY, report)));
    }

    @Test
    void lateReport_forAnOlderMatch_isIgnored() {
        reports.markPending("ABCD", "match-2"); // a rematch finished after match-1

        assertThat(reports.complete("ABCD", "match-1", report)).isEmpty();

        assertThat(reports.get("ABCD")).get()
                .extracting(CoachReportService.View::status)
                .isEqualTo(CoachReportService.Status.PENDING);
    }

    @Test
    void report_forARoomWithNoPendingMatch_isIgnored() {
        assertThat(reports.complete("ZZZZ", "match-1", report)).isEmpty();
        verify(messaging, never()).convertAndSend(eq("/topic/matches/ZZZZ/report"), any(Object.class));
    }
}
