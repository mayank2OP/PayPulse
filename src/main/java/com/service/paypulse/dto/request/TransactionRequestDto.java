package com.service.paypulse.dto.request;

import com.service.paypulse.enums.TransactionType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransactionRequestDto (
        @NotNull(message = "Transaction type is required")
        TransactionType type,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Asset code is required")
        String assetCode,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        Long amount,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey
) {
}
