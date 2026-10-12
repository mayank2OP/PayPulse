package com.service.paypulse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.service.paypulse.dto.request.TransactionRequestDto;
import com.service.paypulse.dto.response.TransactionOutcome;
import com.service.paypulse.enums.AccountType;
import com.service.paypulse.enums.LedgerEntryType;
import com.service.paypulse.enums.Status;
import com.service.paypulse.enums.TransactionType;
import com.service.paypulse.exception.IdempotencyConflictException;
import com.service.paypulse.exception.InsufficientFundsException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final UUID USER_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID SYSTEM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ObjectProvider<TransactionService> self;

    private TransactionService service;
    private User user;
    private Asset asset;
    private Account userAccount;
    private Account systemAccount;

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                transactionRepository,
                ledgerEntryRepository,
                userRepository,
                assetRepository,
                accountRepository,
                self
        );

        user = new User();
        user.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        user.setEmail("aditisingh@gmail.com");

        asset = new Asset();
        asset.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        asset.setCode("GOLD_COINS");
        asset.setName("Gold Coins");

        userAccount = account(USER_ACCOUNT_ID, AccountType.USER, 500);
        userAccount.setUser(user);
        userAccount.setAsset(asset);
        systemAccount = account(SYSTEM_ACCOUNT_ID, AccountType.SYSTEM, -800);
        systemAccount.setAsset(asset);
    }

    @Test
    void topUpCreditsTheUserAndDebitsTheTreasury() {
        when(self.getObject()).thenReturn(service);
        stubAccounts();
        when(transactionRepository.save(any())).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            transaction.setCreatedAt(Instant.parse("2026-02-22T12:00:00Z"));
            return transaction;
        });

        TransactionOutcome outcome = service.processTransaction(request(TransactionType.TOPUP, 100, "topup-1"));

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.response().amount()).isEqualTo(100);
        assertThat(outcome.response().balanceAfter()).isEqualTo(600);
        assertThat(userAccount.getBalance()).isEqualTo(600);
        assertThat(systemAccount.getBalance()).isEqualTo(-900);
        assertThat(outcome.response().transactionType()).isEqualTo(TransactionType.TOPUP);
    }

    @Test
    void spendLocksTheLowerAccountIdFirstAndRejectsAnOverdraft() {
        when(self.getObject()).thenReturn(service);
        stubAccounts();

        assertThatThrownBy(() -> service.processTransaction(request(TransactionType.SPEND, 800, "spend-1")))
                .isInstanceOf(InsufficientFundsException.class);

        InOrder locks = inOrder(accountRepository);
        locks.verify(accountRepository).findByAccountId(SYSTEM_ACCOUNT_ID);
        locks.verify(accountRepository).findByAccountId(USER_ACCOUNT_ID);
        verify(transactionRepository, never()).save(any());
        assertThat(userAccount.getBalance()).isEqualTo(500);
    }

    @Test
    void replayReturnsTheOriginalBalanceAndDoesNotPostAgain() {
        Transaction existing = storedTransaction(TransactionType.TOPUP, "topup-1");
        LedgerEntry userEntry = ledgerEntry(userAccount, existing, LedgerEntryType.CREDIT, 100);
        when(transactionRepository.findByIdempotencyKey("topup-1")).thenReturn(Optional.of(existing));
        when(ledgerEntryRepository.findByTransactionId(existing.getId())).thenReturn(List.of(userEntry));
        when(ledgerEntryRepository.balanceAsOf(USER_ACCOUNT_ID, existing.getCreatedAt(), existing.getId())).thenReturn(600L);

        TransactionOutcome outcome = service.processTransaction(request(TransactionType.TOPUP, 100, "topup-1"));

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.response().transactionId()).isEqualTo(existing.getId());
        assertThat(outcome.response().amount()).isEqualTo(100);
        assertThat(outcome.response().balanceAfter()).isEqualTo(600);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void reusingAKeyForADifferentAmountIsRejected() {
        Transaction existing = storedTransaction(TransactionType.TOPUP, "topup-1");
        when(transactionRepository.findByIdempotencyKey("topup-1")).thenReturn(Optional.of(existing));
        when(ledgerEntryRepository.findByTransactionId(existing.getId()))
                .thenReturn(List.of(ledgerEntry(userAccount, existing, LedgerEntryType.CREDIT, 100)));

        assertThatThrownBy(() -> service.processTransaction(request(TransactionType.TOPUP, 50, "topup-1")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aDuplicateInsertReturnsTheTransactionThatWonTheRace() {
        TransactionService executor = org.mockito.Mockito.mock(TransactionService.class);
        Transaction existing = storedTransaction(TransactionType.BONUS, "bonus-1");
        when(self.getObject()).thenReturn(executor);
        when(transactionRepository.findByIdempotencyKey("bonus-1"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(executor.execute(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(ledgerEntryRepository.findByTransactionId(existing.getId()))
                .thenReturn(List.of(ledgerEntry(userAccount, existing, LedgerEntryType.CREDIT, 25)));
        when(ledgerEntryRepository.balanceAsOf(USER_ACCOUNT_ID, existing.getCreatedAt(), existing.getId())).thenReturn(525L);

        TransactionOutcome outcome = service.processTransaction(request(TransactionType.BONUS, 25, "  bonus-1  "));

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.response().amount()).isEqualTo(25);
        assertThat(outcome.response().balanceAfter()).isEqualTo(525);
    }

    private void stubAccounts() {
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("aditisingh@gmail.com")).thenReturn(Optional.of(user));
        when(assetRepository.findByCodeIgnoreCase("GOLD_COINS")).thenReturn(Optional.of(asset));
        when(accountRepository.findByUserIdAndAssetId(user.getId(), asset.getId())).thenReturn(Optional.of(userAccount));
        when(accountRepository.findByTypeAndAssetId(AccountType.SYSTEM, asset.getId())).thenReturn(Optional.of(systemAccount));
        when(accountRepository.findByAccountId(SYSTEM_ACCOUNT_ID)).thenReturn(Optional.of(systemAccount));
        when(accountRepository.findByAccountId(USER_ACCOUNT_ID)).thenReturn(Optional.of(userAccount));
    }

    private TransactionRequestDto request(TransactionType type, long amount, String idempotencyKey) {
        return new TransactionRequestDto(type, " AditiSingh@gmail.com ", "gold_coins", amount, idempotencyKey);
    }

    private Account account(UUID id, AccountType type, long balance) {
        Account account = new Account();
        account.setId(id);
        account.setType(type);
        account.setBalance(balance);
        return account;
    }

    private Transaction storedTransaction(TransactionType type, String idempotencyKey) {
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setType(type);
        transaction.setStatus(Status.SUCCESS);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setCreatedAt(Instant.parse("2026-02-22T12:00:00Z"));
        return transaction;
    }

    private LedgerEntry ledgerEntry(Account account, Transaction transaction, LedgerEntryType type, long amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccount(account);
        entry.setTransaction(transaction);
        entry.setType(type);
        entry.setAmount(amount);
        return entry;
    }
}
