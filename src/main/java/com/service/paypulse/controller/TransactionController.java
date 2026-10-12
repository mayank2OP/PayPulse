package com.service.paypulse.controller;

import java.util.List;

import com.service.paypulse.dto.request.TransactionRequestDto;
import com.service.paypulse.dto.response.BalanceResponseDto;
import com.service.paypulse.dto.response.ListTransactionResponseDto;
import com.service.paypulse.dto.response.TransactionOutcome;
import com.service.paypulse.dto.response.TransactionResponseDto;
import com.service.paypulse.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponseDto> createTransaction(@Valid @RequestBody TransactionRequestDto transactionRequestDto) {
        TransactionOutcome outcome = transactionService.processTransaction(transactionRequestDto);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.response());
    }

    @GetMapping({"/balance", "/wallet/balance"})
    public ResponseEntity<BalanceResponseDto> getBalance(
            @RequestParam @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
            @RequestParam @NotBlank(message = "Asset code is required") String assetCode
    ) {
        return ResponseEntity.ok(transactionService.getBalance(email, assetCode));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<ListTransactionResponseDto>> getTransactions(
            @RequestParam @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email
    ) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(email));
    }
}
