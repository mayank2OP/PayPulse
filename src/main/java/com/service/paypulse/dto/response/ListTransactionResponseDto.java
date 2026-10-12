package com.service.paypulse.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.service.paypulse.enums.Status;
import com.service.paypulse.enums.TransactionType;

public record ListTransactionResponseDto(
        UUID transactionId,
        TransactionType transactionType,
        Status status,
        Instant createdAt,
        Long amount
) {
}
