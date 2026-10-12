package com.service.paypulse.dto.response;

public record TransactionOutcome(
        TransactionResponseDto response,
        boolean created
) {
    public static TransactionOutcome created(TransactionResponseDto response) {
        return new TransactionOutcome(response, true);
    }

    public static TransactionOutcome replay(TransactionResponseDto response) {
        return new TransactionOutcome(response, false);
    }
}
