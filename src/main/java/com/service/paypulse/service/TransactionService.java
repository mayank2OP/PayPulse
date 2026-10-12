package com.service.paypulse.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.service.paypulse.dto.request.TransactionRequestDto;
import com.service.paypulse.dto.response.BalanceResponseDto;
import com.service.paypulse.dto.response.ListTransactionResponseDto;
import com.service.paypulse.dto.response.TransactionOutcome;
import com.service.paypulse.dto.response.TransactionResponseDto;
import com.service.paypulse.enums.AccountType;
import com.service.paypulse.enums.LedgerEntryType;
import com.service.paypulse.enums.Status;
import com.service.paypulse.enums.TransactionType;
import com.service.paypulse.exception.IdempotencyConflictException;
import com.service.paypulse.exception.InsufficientFundsException;
import com.service.paypulse.exception.ResourceNotFoundException;
import com.service.paypulse.model.Account;
import com.service.paypulse.model.Asset;
import com.service.paypulse.model.LedgerEntry;
import com.service.paypulse.model.Transaction;
import com.service.paypulse.model.User;
import com.service.paypulse.repository.AccountRepository;
import com.service.paypulse.repository.AssetRepository;
import com.service.paypulse.repository.LedgerEntryRepository;
import com.service.paypulse.repository.TransactionRepository;
import com.service.paypulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final ObjectProvider<TransactionService> self;

    public TransactionOutcome processTransaction(TransactionRequestDto request) {
        TransactionRequestDto normalized = normalize(request);
        return transactionRepository.findByIdempotencyKey(normalized.idempotencyKey())
                .map(existing -> TransactionOutcome.replay(replay(existing, normalized)))
                .orElseGet(() -> createOrReplay(normalized));
    }

    @Transactional
    public TransactionResponseDto execute(TransactionRequestDto request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Asset asset = assetRepository.findByCodeIgnoreCase(request.assetCode())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        Account userAccount = accountRepository.findByUserIdAndAssetId(user.getId(), asset.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        Account systemAccount = accountRepository.findByTypeAndAssetId(AccountType.SYSTEM, asset.getId())
                .orElseThrow(() -> new ResourceNotFoundException("System Account not found"));

        LockedAccounts locked = lockInOrder(userAccount.getId(), systemAccount.getId());

        if (request.type() == TransactionType.SPEND) {
            return spend(request, locked);
        }
        return credit(request, locked);
    }

    @Transactional(readOnly = true)
    public BalanceResponseDto getBalance(String email, String assetCode) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Asset asset = assetRepository.findByCodeIgnoreCase(normalizeAssetCode(assetCode))
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        Account account = accountRepository.findByUserIdAndAssetId(user.getId(), asset.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        return new BalanceResponseDto(
                user.getId(),
                user.getEmail(),
                asset.getName(),
                account.getBalance()
        );
    }

    @Transactional(readOnly = true)
    public List<ListTransactionResponseDto> getTransactionHistory(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }
        return transactionRepository.findByUserEmail(normalizedEmail);
    }

    private TransactionOutcome createOrReplay(TransactionRequestDto request) {
        try {
            return TransactionOutcome.created(self.getObject().execute(request));
        } catch (DataIntegrityViolationException ex) {
            return transactionRepository.findByIdempotencyKey(request.idempotencyKey())
                    .map(existing -> TransactionOutcome.replay(replay(existing, request)))
                    .orElseThrow(() -> ex);
        }
    }

    private TransactionResponseDto credit(TransactionRequestDto request, LockedAccounts locked) {
        long amount = request.amount();
        locked.user().setBalance(Math.addExact(locked.user().getBalance(), amount));
        locked.system().setBalance(Math.subtractExact(locked.system().getBalance(), amount));
        return post(request, locked, LedgerEntryType.CREDIT, LedgerEntryType.DEBIT);
    }

    private TransactionResponseDto spend(TransactionRequestDto request, LockedAccounts locked) {
        long amount = request.amount();
        if (locked.user().getBalance() < amount) {
            throw new InsufficientFundsException("Insufficient balance");
        }
        locked.user().setBalance(Math.subtractExact(locked.user().getBalance(), amount));
        locked.system().setBalance(Math.addExact(locked.system().getBalance(), amount));
        return post(request, locked, LedgerEntryType.DEBIT, LedgerEntryType.CREDIT);
    }

    private TransactionResponseDto post(
            TransactionRequestDto request,
            LockedAccounts locked,
            LedgerEntryType userEntryType,
            LedgerEntryType systemEntryType
    ) {
        Transaction transaction = new Transaction();
        transaction.setType(request.type());
        transaction.setIdempotencyKey(request.idempotencyKey());
        transaction.setStatus(Status.SUCCESS);
        transactionRepository.save(transaction);

        LedgerEntry userEntry = ledgerEntry(locked.user(), transaction, userEntryType, request.amount());
        LedgerEntry systemEntry = ledgerEntry(locked.system(), transaction, systemEntryType, request.amount());
        ledgerEntryRepository.saveAll(List.of(userEntry, systemEntry));
        accountRepository.saveAll(List.of(locked.user(), locked.system()));

        return new TransactionResponseDto(
                transaction.getId(),
                transaction.getType(),
                request.amount(),
                locked.user().getBalance(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }

    private TransactionResponseDto replay(Transaction existing, TransactionRequestDto request) {
        LedgerEntry userEntry = userLedgerEntry(existing);
        Account account = userEntry.getAccount();
        boolean sameRequest = existing.getType() == request.type()
                && userEntry.getAmount() == request.amount()
                && account.getUser().getEmail().equalsIgnoreCase(request.email())
                && account.getAsset().getCode().equalsIgnoreCase(request.assetCode());
        if (!sameRequest) {
            throw new IdempotencyConflictException("Idempotency key was already used with a different request");
        }

        Long balanceAfter = ledgerEntryRepository.balanceAsOf(
                account.getId(),
                existing.getCreatedAt(),
                existing.getId()
        );

        return new TransactionResponseDto(
                existing.getId(),
                existing.getType(),
                userEntry.getAmount(),
                balanceAfter == null ? 0L : balanceAfter,
                existing.getStatus(),
                existing.getCreatedAt()
        );
    }

    private LedgerEntry userLedgerEntry(Transaction transaction) {
        return ledgerEntryRepository.findByTransactionId(transaction.getId()).stream()
                .filter(entry -> entry.getAccount() != null && entry.getAccount().getType() == AccountType.USER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Transaction is missing a user ledger entry"));
    }

    private LockedAccounts lockInOrder(UUID userAccountId, UUID systemAccountId) {
        UUID firstId = userAccountId.compareTo(systemAccountId) < 0 ? userAccountId : systemAccountId;
        UUID secondId = firstId.equals(userAccountId) ? systemAccountId : userAccountId;

        Account first = accountRepository.findByAccountId(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        Account second = accountRepository.findByAccountId(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        Account lockedUser = userAccountId.equals(first.getId()) ? first : second;
        Account lockedSystem = systemAccountId.equals(first.getId()) ? first : second;
        return new LockedAccounts(lockedUser, lockedSystem);
    }

    private LedgerEntry ledgerEntry(Account account, Transaction transaction, LedgerEntryType type, long amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccount(account);
        entry.setTransaction(transaction);
        entry.setType(type);
        entry.setAmount(amount);
        return entry;
    }

    private TransactionRequestDto normalize(TransactionRequestDto request) {
        return new TransactionRequestDto(
                request.type(),
                normalizeEmail(request.email()),
                normalizeAssetCode(request.assetCode()),
                request.amount(),
                request.idempotencyKey().trim()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAssetCode(String assetCode) {
        return assetCode.trim().toUpperCase(Locale.ROOT);
    }

    private record LockedAccounts(Account user, Account system) {
    }
}
