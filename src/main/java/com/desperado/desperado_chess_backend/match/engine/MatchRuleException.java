package com.desperado.desperado_chess_backend.match.engine;

public class MatchRuleException extends RuntimeException {

    public enum Reason { MATCH_OVER, BOARD_NOT_FOUND, NOT_YOUR_BOARD, NOT_YOUR_TURN, ILLEGAL_MOVE }

    private final Reason reason;

    public MatchRuleException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}