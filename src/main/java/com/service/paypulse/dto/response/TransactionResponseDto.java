package com.service.paypulse.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.service.paypulse.enums.Status;
import com.service.paypulse.enums.TransactionType;

public record TransactionResponseDto(
        UUID transactionId,
        TransactionType transactionType,
        Long amount,
        Long balanceAfter,
        Status status,
        Instant createdAt
) {
}
