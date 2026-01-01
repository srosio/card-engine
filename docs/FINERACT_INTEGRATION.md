# Apache Fineract Integration Guide

This document provides detailed instructions for integrating Card Engine with Apache Fineract as the backing ledger system.

## Overview

Apache Fineract is an open-source core banking platform. When integrated with Card Engine, Fineract serves as the **authoritative ledger** for:
- Account balances
- Transaction history
- General ledger entries
- Financial reporting

Card Engine handles:
- Card lifecycle management
- Authorization rules and fraud detection
- Settlement coordination
- Card-specific transaction tracking

## Architecture

```
┌─────────────────────────────────────────┐
│         Card Engine                      │
│  ┌────────────┐  ┌──────────────┐       │
│  │Authorization│  │  Settlement  │       │
│  │   Service  │  │   Service    │       │
│  └────────────┘  └──────────────┘       │
│         │                │               │
│         ▼                ▼               │
│  ┌──────────────────────────────┐       │
│  │  FineractAccountAdapter      │       │
│  │  - getBalance()              │       │
│  │  - reserve()                 │       │
│  │  - commit()                  │       │
│  │  - release()                 │       │
│  └──────────────────────────────┘       │
│         │                                │
└─────────┼────────────────────────────────┘
          │
          ▼ HTTP API
┌─────────────────────────────────────────┐
│      Apache Fineract                     │
│  ┌──────────────┐  ┌──────────────┐     │
│  │Savings       │  │  General     │     │
│  │Accounts      │  │  Ledger      │     │
│  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────┘
```

## Authorization Hold Challenge

**Problem**: Fineract doesn't natively support card-style authorization holds.

Card payments work in two stages:
1. **Authorization**: Reserve funds immediately when card is swiped
2. **Clearing**: Actually move the funds (1-3 days later)

During this window, funds must be unavailable for other transactions but not yet removed from the account.

## Authorization Hold Solution

We implement authorization holds using **shadow journal entries** with a dedicated GL account.

### Setup: Create CARD_AUTH_HOLDS GL Account

First, create a special GL account in Fineract to hold reserved funds:

```
GL Account Name: Card Authorization Holds
GL Code: 2100 (or your choice)
Account Type: LIABILITY
Account Usage: DETAIL
Manual Entries Allowed: Yes
```

Record this GL account ID - you'll need it for configuration.

### How It Works

#### 1. RESERVE (Authorization)

When a card authorization occurs:

```
Journal Entry:
  DEBIT:  Customer Savings Account (reduces available balance)
  CREDIT: CARD_AUTH_HOLDS GL Account (holds the funds)

Reference: {authorizationId}
Comment: "Card authorization hold - {authorizationId}"
```

**Result**:
- Customer's available balance decreases
- Funds are held in CARD_AUTH_HOLDS
- Fineract ledger remains balanced
- Hold reference stored in `fineract_auth_holds` table

#### 2. COMMIT (Clearing)

When the transaction clears:

```
Step 1 - Reverse the hold:
  DEBIT:  CARD_AUTH_HOLDS GL Account
  CREDIT: Customer Savings Account

Step 2 - Make actual debit:
  Savings Withdrawal Transaction:
    Amount: {clearingAmount}
    Reference: {authorizationId}
```

**Result**:
- Hold is released
- Funds are actually debited from account
- Transaction appears in Fineract transaction history
- Hold marked as COMMITTED in database

#### 3. RELEASE (Cancellation/Expiry)

When authorization expires or is cancelled:

```
Journal Entry (reversal):
  DEBIT:  CARD_AUTH_HOLDS GL Account
  CREDIT: Customer Savings Account

Reference: {authorizationId}
Comment: "Release expired authorization"
```

**Result**:
- Funds returned to customer's available balance
- No debit made
- Hold marked as RELEASED in database

## Configuration

### 1. Application Configuration

Edit `application.yml`:

```yaml
card-engine:
  fineract:
    enabled: true
    base-url: http://localhost:8443/fineract-provider/api/v1
    tenant: default  # Your Fineract tenant
    username: mifos  # Fineract API user
    password: password  # Fineract API password
    card-auth-holds-gl-account-id: 2100  # GL account created above
```

### 2. Fineract Prerequisites

**Required Fineract Setup**:

1. ✅ Fineract instance running and accessible
2. ✅ API user with appropriate permissions
3. ✅ CARD_AUTH_HOLDS GL account created
4. ✅ Savings products configured for customers
5. ✅ Customer savings accounts created

**API Permissions Required**:
- Read savings accounts
- Create journal entries
- Create savings transactions (withdrawals, deposits)

### 3. Database Migration

Card Engine requires these tables for Fineract integration:

```sql
-- Tracks authorization holds in Fineract
CREATE TABLE fineract_auth_holds (
    id VARCHAR(255) PRIMARY KEY,
    authorization_id VARCHAR(255) UNIQUE NOT NULL,
    fineract_account_id BIGINT NOT NULL,
    journal_entry_id BIGINT,
    hold_amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_auth_hold_auth_id ON fineract_auth_holds(authorization_id);
CREATE INDEX idx_auth_hold_account_id ON fineract_auth_holds(fineract_account_id);
```

These are created automatically when using `ddl-auto: update` in JPA configuration.

## Usage

### Creating a Fineract-backed Account

```java
@Autowired
private FineractClient fineractClient;

@Autowired
private FineractAuthHoldRepository holdRepository;

// Create adapter linked to Fineract savings account
FineractAccountAdapter account = new FineractAccountAdapter(
    fineractClient,
    holdRepository,
    "internal-account-id",
    100L,  // Fineract savings account ID
    Currency.USD,
    2100L  // CARD_AUTH_HOLDS GL account ID
);

// Use like any other account
Money balance = account.getBalance();
account.reserve(Money.of("50.00", Currency.USD), "auth-123");
account.commit(Money.of("50.00", Currency.USD), "auth-123");
```

### Linking Cards to Fineract Accounts

When issuing a card, specify the Fineract account:

```java
// 1. Customer already has Fineract savings account (ID: 100)

// 2. Create FineractAccountAdapter
FineractAccountAdapter fineractAccount = new FineractAccountAdapter(
    fineractClient,
    holdRepository,
    "account-xyz",
    100L,  // Link to Fineract account
    Currency.USD,
    2100L
);

// 3. Issue card backed by this account
Card card = cardService.issueCard(
    "John Doe",
    "1234",
    LocalDate.now().plusYears(2),
    fineractAccount.getAccountId(),  // Uses Fineract for funds
    "customer-123"
);
```

## Transaction Flow Example

### Complete Flow: Authorization → Clearing

```java
// 1. Customer swipes card at merchant for $50
AuthorizationRequest authRequest = AuthorizationRequest.builder()
    .authorizationId("auth-001")
    .cardId(cardId)
    .amount(Money.of("50.00", Currency.USD))
    .merchantName("Coffee Shop")
    .build();

// Authorization service processes:
// - Checks rules
// - Calls fineractAccount.reserve(50, "auth-001")
//   → Creates journal entry in Fineract
//   → Stores hold in fineract_auth_holds table
AuthorizationResponse response = authorizationService.authorize(authRequest);
// Response: APPROVED

// At this point in Fineract:
// - Customer account available balance: $950 (was $1000)
// - CARD_AUTH_HOLDS GL balance: $50
// - Ledger balanced ✓

// 2. Three days later, transaction clears
ClearingRequest clearRequest = ClearingRequest.builder()
    .authorizationId("auth-001")
    .clearingAmount(Money.of("50.00", Currency.USD))
    .build();

// Settlement service processes:
// - Calls fineractAccount.commit(50, "auth-001")
//   → Reverses hold journal entry
//   → Makes withdrawal transaction
//   → Marks hold as COMMITTED
settlementService.clearTransaction(clearRequest);

// Final state in Fineract:
// - Customer account balance: $950
// - CARD_AUTH_HOLDS GL balance: $0
// - Transaction appears in customer's statement
// - Ledger balanced ✓
```

## Monitoring and Reconciliation

### Check Hold Status

```java
// Find all active holds for an account
List<FineractAuthHold> activeHolds = holdRepository
    .findByFineractAccountIdAndStatus(
        fineractAccountId,
        FineractAuthHold.HoldStatus.ACTIVE
    );

// Calculate total reserved
BigDecimal totalReserved = activeHolds.stream()
    .map(FineractAuthHold::getHoldAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

### Reconciliation

Daily reconciliation should verify:

1. **CARD_AUTH_HOLDS GL Account Balance** = Sum of ACTIVE holds in database
2. **Customer Available Balance** = Account Balance - Sum of ACTIVE holds
3. **No orphaned holds** (holds without corresponding authorization)

```sql
-- Query to find orphaned holds
SELECT h.*
FROM fineract_auth_holds h
LEFT JOIN authorizations a ON h.authorization_id = a.authorization_id
WHERE h.status = 'ACTIVE'
  AND a.authorization_id IS NULL;
```

## Error Handling

### Fineract API Failures

All Fineract API calls include error handling:

```java
try {
    fineractClient.getAccountBalance(accountId);
} catch (Exception e) {
    log.error("Fineract API error", e);
    // Returns exception to caller
    // Authorization would be DECLINED
}
```

### Idempotency

All operations are idempotent:

- **Duplicate reserve()**: Checks for existing hold, returns immediately
- **Duplicate commit()**: Checks hold status, prevents double debit
- **Duplicate release()**: Safe to call multiple times

### Retry Logic

For transient failures, implement retry at the application level:

```java
@Retryable(
    value = {RestClientException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000)
)
public FineractDTOs.AccountBalance getBalance(Long accountId) {
    return fineractClient.getAccountBalance(accountId);
}
```

## Performance Considerations

### Latency

- **Balance Check**: ~50-200ms (Fineract API call)
- **Reserve (Authorization)**: ~100-300ms (journal entry creation)
- **Commit (Clearing)**: ~150-400ms (reverse + debit)

For card-present transactions, ensure total authorization time < 500ms.

### Optimization

1. **Connection Pooling**: Configure HTTP client with connection pool
2. **Caching**: Cache account balances briefly (1-5 seconds)
3. **Async Clearing**: Process clearing webhooks asynchronously
4. **Batch Processing**: For bulk operations, use Fineract batch API

## Security

### API Credentials

- Store Fineract credentials in environment variables, not config files
- Use different credentials per environment (dev/staging/prod)
- Rotate credentials regularly

### Network Security

- Always use HTTPS in production
- Fineract API should not be publicly accessible
- Use firewall rules to restrict access

### Audit Trail

All operations are logged:
- Card Engine: Authorization/settlement logs
- Fineract: Journal entries and transactions
- Database: Hold status changes

## Troubleshooting

### "Insufficient funds" but balance looks correct

**Cause**: Active holds reducing available balance

**Solution**: Check active holds:
```java
Money reserved = fineractAccount.getReservedBalance();
Money available = fineractAccount.getBalance();
```

### CARD_AUTH_HOLDS balance not zero

**Cause**: Holds not being released/committed properly

**Solution**:
1. Query active holds in database
2. Check corresponding authorizations
3. Release/commit as needed
4. Investigate why holds weren't processed

### Fineract API timeout

**Cause**: Fineract instance overloaded or unreachable

**Solution**:
1. Check Fineract instance health
2. Review network connectivity
3. Implement circuit breaker pattern
4. Consider read replicas for balance checks

## Production Deployment

### Checklist

- [ ] Fineract production instance configured
- [ ] CARD_AUTH_HOLDS GL account created
- [ ] API credentials secured
- [ ] HTTPS enforced
- [ ] Connection pooling configured
- [ ] Monitoring and alerting set up
- [ ] Reconciliation jobs scheduled
- [ ] Disaster recovery plan documented

### Scaling

For high transaction volume:
- Use Fineract read replicas for balance checks
- Consider async settlement processing
- Implement rate limiting on Fineract API calls
- Monitor CARD_AUTH_HOLDS account size

## Additional Resources

- Apache Fineract Documentation: https://fineract.apache.org/
- Fineract API Docs: https://demo.fineract.dev/fineract-provider/api-docs/apiLive.htm
- Card Engine Architecture: `docs/ARCHITECTURE.md`
