package com.desperado.desperado_chess_backend.common;

import com.desperado.desperado_chess_backend.match.engine.MatchRuleException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MatchRuleException.class)
    public ProblemDetail handleMatchRule(MatchRuleException e) {
        HttpStatus status = switch (e.getReason()) {
            case BOARD_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_YOUR_BOARD -> HttpStatus.FORBIDDEN;
            case NOT_YOUR_TURN, MATCH_OVER -> HttpStatus.CONFLICT;
            case ILLEGAL_MOVE -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return problem(status, e.getMessage(), e.getReason().name());
    }

    /** Two simultaneous joins can both pass the checks; the database's unique constraints catch the second. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConstraintViolation(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "That seat or nickname was just taken. Please try again.", "CONFLICT");
    }

    private ProblemDetail problem(HttpStatus status, String detail, String reason) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("reason", reason);
        problem.setProperty("message", detail); // what the frontend's getErrorMessage reads
        return problem;
    }
}