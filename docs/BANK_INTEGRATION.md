# Bank Core Integration Guide

This document explains how to integrate Card Engine with your existing core banking system.

## Overview

Card Engine is designed to integrate with existing core banking systems (CBS) where:
- **Bank Core = Source of Truth** for all client data, account balances, and transactions
- **Card Engine = Payment Instrument Layer** that issues cards and processes authorizations
- **No Data Duplication**: Card Engine never owns or mirrors balances

## Core Principles

### 1. Bank Core is the Single Source of Truth

Your core banking system:
- ✅ Manages clients/customers
- ✅ Manages deposit and ledger accounts
- ✅ Is the authoritative system for balances
- ✅ Records all transactions
- ✅ Provides balance and transaction APIs

Card Engine:
- ✅ Manages card lifecycle (issue, activate, freeze, close)
- ✅ Processes authorization requests (real-time)
- ✅ Enforces card rules (limits, MCC blocking, velocity)
- ✅ Coordinates settlement
- ❌ Never owns account balances
- ❌ Never creates clients or accounts
- ❌ Never duplicates core banking data

### 2. Clients and Accounts Exist Before Cards

**Prerequisite Flow**:
```
1. Client onboarding (in bank core - KYC, compliance)
   ↓
2. Account opening (in bank core - deposit/savings account created)
   ↓
3. Account funded (client deposits money into bank core account)
   ↓
4. Card issuance (Card Engine links card to existing bank account)
```

**Card Engine is NOT**:
- A bank
- An account opening system
- A KYC/compliance platform
- A wallet or balance holder

### 3. Cards are Payment Instruments

Think of cards as:
- **Access keys** to existing bank accounts
- **Authorization mechanisms** that check balance and apply rules
- **NOT account holders** themselves

When a card transaction occurs:
1. Authorization request arrives
2. Card Engine maps card → bank account
3. Card Engine checks balance in **bank core** (not locally)
4. Card Engine places hold in **bank core**
5. Bank core enforces the hold
6. Card Engine returns approval/decline

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Bank Core System                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Clients    │  │   Accounts   │  │   Ledger     │  │
│  │  (Customers) │  │  (Balances)  │  │(Transactions)│  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│         SOURCE OF TRUTH FOR ALL BALANCES                 │
└─────────────────────────────────────────────────────────┘
                        ▲
                        │ BankAccountAdapter Interface
                        │ (Balance checks, holds, debits)
                        │
┌─────────────────────────────────────────────────────────┐
│                   Card Engine                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Cards      │  │ Bank Account │  │Authorization │  │
│  │              │  │   Mappings   │  │    Rules     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│         MANAGES CARDS + AUTHORIZATION LOGIC               │
└─────────────────────────────────────────────────────────┘
```

## Integration Interface: BankAccountAdapter

Card Engine defines a generic interface that ANY bank core can implement:

```java
public interface BankAccountAdapter {
    // Check real-time balance in bank core
    Money getAvailableBalance(String accountRef);

    // Place authorization hold in bank core
    void placeHold(String accountRef, Money amount, String referenceId);

    // Commit debit in bank core (clearing)
    void commitDebit(String accountRef, Money amount, String referenceId);

    // Release hold without debiting
    void releaseHold(String accountRef, Money amount, String referenceId);

    String getAdapterName();
    boolean isHealthy();
}
```

### Vendor Neutrality

This interface works with:
- ✅ Apache Fineract (open-source, reference implementation provided)
- ✅ Temenos T24/Transact
- ✅ Mambu
- ✅ Oracle FLEXCUBE
- ✅ FIS cores (Systematics, Horizon, etc.)
- ✅ Jack Henry cores (Silverlake, CIF 20/20, etc.)
- ✅ Custom/proprietary cores

## Reference Implementation: Apache Fineract

Card Engine provides a complete Apache Fineract adapter as a reference.

### Why Apache Fineract?

- Open-source core banking platform
- Well-documented REST API
- Widely used by financial institutions
- Good example for other integrations

### Fineract Adapter Implementation

See `com.cardengine.bank.fineract.FineractBankAccountAdapter`

**Key features**:
- Real-time balance checks via Fineract API
- Authorization hold workaround (shadow journal entries)
- Idempotent operations
- Full error handling

**Authorization Hold Pattern**:
Since Fineract doesn't natively support card holds, the adapter uses shadow transactions:

1. **placeHold()**: Create journal entry (DEBIT savings → CREDIT CARD_AUTH_HOLDS GL account)
2. **commitDebit()**: Reverse hold entry + make withdrawal
3. **releaseHold()**: Reverse hold entry

This keeps Fineract's ledger balanced while providing proper fund reservation.

## Implementing Your Own Adapter

### Step 1: Understand Your Core Banking API

Document your bank core's:
- Account balance query endpoint
- Transaction/debit posting endpoint
- Hold/reservation mechanism (if any)
- Authentication requirements
- Rate limits and timeouts

### Step 2: Implement BankAccountAdapter

```java
@Component
public class YourBankAdapter implements BankAccountAdapter {

    private final YourBankClient bankClient;  // Your bank's API client

    @Override
    public Money getAvailableBalance(String accountRef) {
        // Call your bank's balance API
        YourBankBalance response = bankClient.getBalance(accountRef);
        return Money.of(response.getAvailableAmount(), response.getCurrency());
    }

    @Override
    public void placeHold(String accountRef, Money amount, String referenceId) {
        // If your bank supports native holds:
        bankClient.placeHold(accountRef, amount, referenceId);

        // If not, implement workaround (see hold patterns below)
    }

    @Override
    public void commitDebit(String accountRef, Money amount, String referenceId) {
        // Release hold (if applicable)
        // Make debit transaction
        bankClient.debitAccount(accountRef, amount, referenceId);
    }

    @Override
    public void releaseHold(String accountRef, Money amount, String referenceId) {
        // Release without debiting
        bankClient.releaseHold(accountRef, referenceId);
    }

    @Override
    public String getAdapterName() {
        return "YourBankName";
    }

    @Override
    public boolean isHealthy() {
        return bankClient.isConnected();
    }
}
```

### Step 3: Handle Authorization Holds

**If your bank core supports native holds/reservations**:
- Use them directly in `placeHold()`
- This is the ideal scenario

**If your bank core does NOT support holds**:
You must implement a workaround. Common patterns:

#### Pattern 1: Shadow Journal Entries (Fineract approach)
```
placeHold:
  Create journal entry: DEBIT account → CREDIT holds GL account

commitDebit:
  Reverse hold entry
  Make actual debit

releaseHold:
  Reverse hold entry
```

#### Pattern 2: Dedicated Hold Account
```
placeHold:
  Transfer funds to "card_holds_[accountId]" account

commitDebit:
  Transfer back from hold account
  Debit original account

releaseHold:
  Transfer back from hold account
```

#### Pattern 3: Pending Transaction Flags
```
placeHold:
  Create pending transaction with status=HELD
  Update available balance calculation

commitDebit:
  Mark pending transaction as POSTED
  Post actual debit

releaseHold:
  Mark pending transaction as CANCELLED
```

**Requirements for any workaround**:
- ✅ Must be idempotent
- ✅ Must prevent duplicate debits
- ✅ Must be auditable in bank core
- ✅ Must maintain balanced ledger
- ✅ Must be safe for bank review

### Step 4: Ensure Idempotency

All operations must be idempotent (safe to retry):

```java
public void placeHold(String accountRef, Money amount, String referenceId) {
    // Check if hold already exists
    if (holdExists(referenceId)) {
        log.info("Hold already placed: {}", referenceId);
        return;  // Idempotent
    }

    // Place hold logic...
}
```

### Step 5: Add Error Handling

```java
public Money getAvailableBalance(String accountRef) {
    try {
        return bankClient.getBalance(accountRef);
    } catch (BankAPIException e) {
        log.error("Bank API error: account={}", accountRef, e);
        throw new BankCoreException(
            "Failed to fetch balance",
            accountRef,
            "getAvailableBalance",
            e
        );
    }
}
```

## Card Issuance Flow

### Prerequisites (Done in Bank Core)
1. ✅ Client exists (KYC completed)
2. ✅ Account exists (opened and funded)
3. ✅ Client has passed compliance checks

### Issuance in Card Engine

```java
// Bank identifies which account should back the card
String bankClientRef = "BANK_CLIENT_12345";
String bankAccountRef = "BANK_ACCOUNT_67890";

// Issue card (creates mapping only, not account)
Card card = bankCardIssuanceService.issueCardForBankAccount(
    bankClientRef,        // Existing bank client ID
    bankAccountRef,       // Existing bank account ID
    "John Doe",           // Cardholder name
    LocalDate.now().plusYears(2),  // Expiration
    "bank-admin"          // Who issued it (audit)
);

// Card starts in FROZEN state for security
// Activate when ready
bankCardIssuanceService.activateCard(card.getCardId());
```

**What happens**:
1. Card Engine verifies account exists (calls bank core)
2. Creates card record
3. Creates immutable mapping: card → bank account
4. Returns card in FROZEN state

**What does NOT happen**:
- No account creation
- No balance initialization
- No client onboarding
- No KYC checks

## Authorization Flow

```
Card swipe/tap at merchant
        ↓
[1] Processor sends auth request to Card Engine
        ↓
[2] Card Engine validates card state
        ↓
[3] Card Engine runs rules (limits, MCC, velocity)
        ↓
[4] Card Engine maps card → bank account
        ↓
[5] Card Engine calls BankAccountAdapter.getAvailableBalance()
        ↓
[6] Bank Core returns real-time balance ← SOURCE OF TRUTH
        ↓
[7] Card Engine checks sufficient funds
        ↓
[8] Card Engine calls BankAccountAdapter.placeHold()
        ↓
[9] Bank Core places hold (funds unavailable)
        ↓
[10] Card Engine returns APPROVED/DECLINED
```

**Key points**:
- Balance check happens in bank core (step 6)
- Hold is placed in bank core (step 9)
- Card Engine coordinates, bank core enforces

## Clearing/Settlement Flow

```
Transaction clears (1-3 days later)
        ↓
[1] Processor sends clearing notification
        ↓
[2] Card Engine finds original authorization
        ↓
[3] Card Engine calls BankAccountAdapter.commitDebit()
        ↓
[4] Bank Core:
    - Releases authorization hold
    - Debits account for final amount
    - Records transaction
        ↓
[5] Card Engine updates local authorization status
```

**Result**:
- Funds actually debited from bank core account
- Bank core ledger has transaction record
- Card Engine has status for correlation

## Testing Your Integration

### 1. Unit Tests with Mocks

```java
@Test
void testAuthorization() {
    MockBankAccountAdapter mockBank = new MockBankAccountAdapter();
    mockBank.createAccount("ACC123", new BigDecimal("1000.00"));

    // Test authorization flow
    Money balance = mockBank.getAvailableBalance("ACC123");
    assertEquals("1000.00", balance.getAmount().toString());

    mockBank.placeHold("ACC123", Money.of("50.00", Currency.USD), "auth-1");

    Money afterHold = mockBank.getAvailableBalance("ACC123");
    assertEquals("950.00", afterHold.getAmount().toString());
}
```

### 2. Integration Tests with Real Bank (Sandbox)

```java
@Test
void testRealBankIntegration() {
    // Uses real bank core sandbox/test environment
    FineractBankAccountAdapter fineractAdapter = ...; // configured for test env

    // Verify connectivity
    assertTrue(fineractAdapter.isHealthy());

    // Test balance check
    Money balance = fineractAdapter.getAvailableBalance("TEST_ACCOUNT_123");
    assertNotNull(balance);

    // Test hold placement
    fineractAdapter.placeHold("TEST_ACCOUNT_123",
        Money.of("10.00", Currency.USD),
        "test-auth-001");

    // Verify hold in bank core
    // (query bank core directly to verify)
}
```

### 3. End-to-End Flow Tests

See `BankIntegrationTest.java` for complete examples.

## Configuration

```yaml
card-engine:
  bank:
    adapter: fineract  # or: custom, t24, mambu, etc.

  # Adapter-specific configuration
  fineract:
    enabled: true
    base-url: https://your-fineract-instance/api/v1
    tenant: default
    username: api-user
    password: ${FINERACT_PASSWORD}  # From environment
    card-auth-holds-gl-account-id: 2100

  # For custom adapters
  custom-bank:
    api-url: https://your-bank-api
    api-key: ${BANK_API_KEY}
    timeout-ms: 3000
```

## Production Considerations

### Performance

**Latency targets**:
- Balance check: < 200ms
- Place hold: < 300ms
- Authorization total: < 500ms (for card-present)

**Optimization strategies**:
- Connection pooling to bank API
- Brief caching of balances (1-5 seconds) if acceptable
- Async settlement processing
- Circuit breakers for bank core failures

### Security

**Network**:
- HTTPS/TLS for all bank core communication
- Mutual TLS if supported
- Firewall rules (Card Engine → Bank Core only)
- No public exposure of bank APIs

**Credentials**:
- Store in secrets manager (AWS Secrets Manager, HashiCorp Vault, etc.)
- Rotate regularly
- Different credentials per environment
- Audit all bank API calls

### Monitoring

**Health checks**:
```java
@Scheduled(fixedRate = 60000)  // Every minute
public void checkBankCoreHealth() {
    if (!bankAccountAdapter.isHealthy()) {
        alerting.sendAlert("Bank core integration unhealthy");
    }
}
```

**Metrics to track**:
- Bank API latency (p50, p95, p99)
- Bank API error rate
- Authorization approval rate
- Hold placement success rate
- Settlement success rate

### Reconciliation

Daily reconciliation should verify:
- Authorization holds in Card Engine match bank core holds
- Cleared transactions in Card Engine match bank core debits
- No orphaned holds (holds without corresponding authorizations)

```sql
-- Example reconciliation query
SELECT
    ce.authorization_id,
    ce.amount,
    bc.hold_status
FROM card_engine.authorizations ce
LEFT JOIN bank_core.holds bc ON ce.authorization_id = bc.reference_id
WHERE ce.status = 'APPROVED'
  AND bc.hold_status IS NULL;  -- Orphaned holds
```

## Migration from Internal Ledger

If you're currently using Card Engine's internal ledger accounts and want to migrate to bank core:

### Phase 1: Parallel Run
1. Keep internal ledger active
2. Add bank adapter (read-only at first)
3. Shadow test: Compare internal vs bank balances
4. Validate hold mechanisms work correctly

### Phase 2: Cutover
1. Stop issuing new cards on internal ledger
2. Issue all new cards with bank core backing
3. Allow existing cards to complete their lifecycle

### Phase 3: Full Migration
1. Migrate remaining cards to bank accounts
2. Reconcile all balances
3. Decommission internal ledger

## Support Matrix

| Bank Core | Native Holds? | Adapter Available | Notes |
|-----------|---------------|-------------------|-------|
| Apache Fineract | No | ✅ Yes (reference) | Uses shadow journal entries |
| Temenos T24 | Yes | ⚠️ Custom needed | Native hold support via TELLER |
| Mambu | Yes | ⚠️ Custom needed | Native hold via HOLD_AMOUNT field |
| Oracle FLEXCUBE | Yes | ⚠️ Custom needed | Native via reservation module |
| FIS Cores | Varies | ⚠️ Custom needed | Depends on specific product |
| Custom Core | Varies | ⚠️ Custom needed | Follow this guide |

## FAQ

**Q: Can Card Engine create bank accounts?**
A: No. Accounts must exist in the bank core before card issuance.

**Q: Where are balances stored?**
A: In the bank core only. Card Engine never stores or mirrors balances.

**Q: What if the bank core doesn't support authorization holds?**
A: Implement one of the workaround patterns (shadow entries, hold accounts, or pending flags). See "Authorization Hold Patterns" above.

**Q: Can one card use multiple bank accounts?**
A: Not in the MVP. One card = one bank account. Multi-account cards can be added later.

**Q: Can multiple cards share one bank account?**
A: Yes. Multiple cards can map to the same bank account (e.g., primary + secondary cardholders).

**Q: What happens if the bank core is unavailable during authorization?**
A: Authorization is declined. Bank core availability is critical for real-time approvals.

**Q: How do reversals/refunds work?**
A: Depends on bank core capabilities. Some support native reversals, others require manual processing or compensating transactions.

**Q: Can I use this for crypto-backed cards?**
A: No. This is designed for bank core integration. For crypto, you'd need a different adapter (not a bank adapter).

## Additional Resources

- **Apache Fineract Integration**: See `docs/FINERACT_INTEGRATION.md`
- **Reference Implementation**: `com.cardengine.bank.fineract.FineractBankAccountAdapter`
- **Mock Adapter for Testing**: `com.cardengine.bank.mock.MockBankAccountAdapter`
- **Integration Tests**: `src/test/java/com/cardengine/bank/BankIntegrationTest.java`

## Getting Help

For integration support:
1. Review reference implementation (Fineract adapter)
2. Check test cases for examples
3. Open GitHub issue with your bank core details
4. For commercial support, contact maintainers

---

**Remember**: This is a card orchestration layer, not a core banking system. The bank core is—and must remain—the source of truth for all financial data.
