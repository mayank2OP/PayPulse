package com.service.paypulse.dto.response;

import java.util.UUID;

public record BalanceResponseDto(
        UUID userId,
        String email,
        String assetName,
        Long balance
) {
}
