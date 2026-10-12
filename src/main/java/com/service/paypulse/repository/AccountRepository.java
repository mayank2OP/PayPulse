package com.service.paypulse.repository;

import java.util.Optional;
import java.util.UUID;

import com.service.paypulse.enums.AccountType;
import com.service.paypulse.model.Account;
import jakarta.persistence.LockModeType;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserIdAndAssetId(UUID userId, UUID assetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ac FROM Account ac WHERE ac.id = :accountId")
    Optional<Account> findByAccountId(@Param("accountId") UUID id);

    Optional<Account> findByTypeAndAssetId(AccountType system, @NonNull UUID id);
}
