package com.service.paypulse.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.service.paypulse.model.LedgerEntry;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountId(UUID accountId);

    @Query("""
            SELECT COALESCE(SUM(
                CASE
                    WHEN le.type = com.service.paypulse.enums.LedgerEntryType.CREDIT THEN le.amount
                    ELSE -le.amount
                END
            ), 0)
            FROM LedgerEntry le
            WHERE le.account.id = :accountId
            """)
    Long sumAmountsByAccountId(@Param("accountId") UUID accountId);

    @Query("""
            SELECT COALESCE(SUM(
                CASE
                    WHEN le.type = com.service.paypulse.enums.LedgerEntryType.CREDIT THEN le.amount
                    ELSE -le.amount
                END
            ), 0)
            FROM LedgerEntry le
            WHERE le.account.id = :accountId
              AND (le.createdAt < :createdAt OR le.transaction.id = :transactionId)
            """)
    Long balanceAsOf(
            @Param("accountId") UUID accountId,
            @Param("createdAt") Instant createdAt,
            @Param("transactionId") UUID transactionId
    );

    List<LedgerEntry> findByTransactionId(@NonNull UUID transactionId);
}
