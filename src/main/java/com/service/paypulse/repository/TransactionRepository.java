package com.service.paypulse.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.service.paypulse.dto.response.ListTransactionResponseDto;
import com.service.paypulse.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT new com.service.paypulse.dto.response.ListTransactionResponseDto(
            t.id, t.type, t.status, t.createdAt, le.amount)
        FROM Transaction t
        JOIN t.ledgerEntries le
        JOIN le.account a
        JOIN a.user u
        WHERE LOWER(u.email) = LOWER(:email)
          AND a.type = com.service.paypulse.enums.AccountType.USER
        ORDER BY t.createdAt DESC
    """)
    List<ListTransactionResponseDto> findByUserEmail(@Param("email") String email);
}
