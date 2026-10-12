package com.service.paypulse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.service.paypulse.dto.response.TransactionOutcome;
import com.service.paypulse.dto.response.TransactionResponseDto;
import com.service.paypulse.enums.Status;
import com.service.paypulse.enums.TransactionType;
import com.service.paypulse.exception.GlobalExceptionHandler;
import com.service.paypulse.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TransactionController(transactionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void aNewTransactionIsCreated() throws Exception {
        when(transactionService.processTransaction(any())).thenReturn(TransactionOutcome.created(sampleResponse()));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TOPUP",
                                  "email": "aditisingh@gmail.com",
                                  "assetCode": "GOLD_COINS",
                                  "amount": 100,
                                  "idempotencyKey": "topup-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(600));
    }

    @Test
    void aRepeatedTransactionIsOk() throws Exception {
        when(transactionService.processTransaction(any())).thenReturn(TransactionOutcome.replay(sampleResponse()));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TOPUP",
                                  "email": "aditisingh@gmail.com",
                                  "assetCode": "GOLD_COINS",
                                  "amount": 100,
                                  "idempotencyKey": "topup-1"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void aNonPositiveAmountIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "SPEND",
                                  "email": "aditisingh@gmail.com",
                                  "assetCode": "GOLD_COINS",
                                  "amount": 0,
                                  "idempotencyKey": "spend-0"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Data"));
    }

    private TransactionResponseDto sampleResponse() {
        return new TransactionResponseDto(
                UUID.randomUUID(),
                TransactionType.TOPUP,
                100L,
                600L,
                Status.SUCCESS,
                Instant.parse("2026-02-22T12:00:00Z")
        );
    }
}
