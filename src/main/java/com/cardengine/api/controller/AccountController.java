package com.cardengine.api.controller;

import com.cardengine.accounts.Account;
import com.cardengine.accounts.AccountService;
import com.cardengine.accounts.AccountType;
import com.cardengine.accounts.BaseAccount;
import com.cardengine.api.dto.CreateAccountRequest;
import com.cardengine.common.IdempotencyKey;
import com.cardengine.common.Money;
import com.cardengine.ledger.LedgerEntry;
import com.cardengine.ledger.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST API for account management.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management API")
public class AccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    @PostMapping
    @Operation(summary = "Create a new account")
    public ResponseEntity<BaseAccount> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Money initialBalance = Money.of(request.getInitialBalance(), request.getCurrency());

        BaseAccount account = switch (request.getAccountType()) {
            case INTERNAL_LEDGER -> accountService.createInternalLedgerAccount(
                request.getOwnerId(), initialBalance);
            case FIAT_WALLET -> accountService.createFiatAccount(
                request.getOwnerId(), initialBalance);
            case STABLECOIN -> accountService.createStablecoinAccount(
                request.getOwnerId(), initialBalance);
            default -> throw new IllegalArgumentException(
                "Account type not supported: " + request.getAccountType());
        };

        // Record initial deposit in ledger
        if (initialBalance.isPositive()) {
            ledgerService.recordDeposit(
                account.getAccountId(),
                initialBalance,
                "Initial deposit",
                IdempotencyKey.generate()
            );
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details")
    public ResponseEntity<Account> getAccount(@PathVariable String accountId) {
        Account account = accountService.getAccount(accountId);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/owner/{ownerId}")
    @Operation(summary = "Get all accounts for an owner")
    public ResponseEntity<List<BaseAccount>> getAccountsByOwner(@PathVariable String ownerId) {
        List<BaseAccount> accounts = accountService.getAccountsByOwner(ownerId);
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/{accountId}/deposit")
    @Operation(summary = "Deposit funds to an account")
    public ResponseEntity<Void> deposit(
            @PathVariable String accountId,
            @RequestParam BigDecimal amount,
            @RequestParam String currency) {

        Money money = Money.of(amount, com.cardengine.common.Currency.valueOf(currency));
        accountService.deposit(accountId, money);

        ledgerService.recordDeposit(
            accountId,
            money,
            "Manual deposit",
            IdempotencyKey.generate()
        );

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{accountId}/ledger")
    @Operation(summary = "Get ledger entries for an account")
    public ResponseEntity<List<LedgerEntry>> getAccountLedger(@PathVariable String accountId) {
        List<LedgerEntry> entries = ledgerService.getAccountLedger(accountId);
        return ResponseEntity.ok(entries);
    }
}
