# Card Engine

**Production-grade open-source card orchestration platform**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## What is Card Engine?

Card Engine is a **bank-agnostic card orchestration platform** that enables banks and financial institutions to issue payment cards backed by their existing core banking systems.

### What Card Engine Is

- ✅ **Card integration layer for banks** - Plugin for existing core banking systems
- ✅ **Bank adapter framework** - Works with any CBS (Fineract, T24, Mambu, FLEXCUBE, etc.)
- ✅ **Card lifecycle management** - Issue, activate, freeze, authorize transactions
- ✅ **Authorization & settlement orchestration** - Routes to bank core for balance checks
- ✅ **Rules engine** - Spending limits, MCC blocking, velocity controls
- ✅ **Processor adapters** - Integrate with card networks and processors

### What Card Engine Is NOT

- ❌ **Not a core banking system** - Bank's CBS is the source of truth
- ❌ **Not an account provider** - Uses existing bank client accounts
- ❌ **Not a ledger** - Bank core owns all balances and transactions
- ❌ **Not a card issuer** - Requires partnership with licensed issuer/processor
- ❌ **Not a compliance platform** - KYC/AML handled by bank's systems

## The Problem

Banks with existing core banking systems want to issue payment cards to their clients:
1. **Clients already exist** in the bank's core system with KYC complete
2. **Accounts already exist** with balances managed by the bank's CBS
3. **Ledger is authoritative** in the bank's core, not in the card system
4. Banks need to integrate with card processors (Marqeta, Lithic, Galileo)
5. Authorization must check balances in real-time from the bank's core
6. Settlement must commit debits to the bank's ledger, not a separate system

Most card platforms assume they **own the ledger and accounts**, creating a mismatch for banks.

## The Solution

Card Engine provides a **bank core integration layer** where your CBS remains the source of truth:

```
┌─────────────────────────────────────────────────────┐
│          Bank's Core Banking System (CBS)           │
│     (Fineract, T24, Mambu, FLEXCUBE, Custom)        │
│                                                     │
│  • Client accounts (pre-existing)                   │
│  • Balances (authoritative)                         │
│  • Transactions (source of truth)                   │
└─────────────────────────────────────────────────────┘
                        ▲
                        │ BankAccountAdapter
                        │ (balance checks, holds, commits)
                        │
┌─────────────────────────────────────────────────────┐
│                  Card Engine                        │
│                                                     │
│  • Card lifecycle (issue, activate, freeze)         │
│  • Authorization orchestration                      │
│  • Settlement coordination                          │
│  • Rules engine (limits, MCC blocking)              │
│  • Card-to-account mapping                          │
└─────────────────────────────────────────────────────┘
                        ▲
                        │ Processor webhooks
                        │
┌─────────────────────────────────────────────────────┐
│          Card Network / Processor                   │
│        (Marqeta, Lithic, Galileo, etc.)             │
└─────────────────────────────────────────────────────┘
```

**Generic bank adapter interface works with ANY core banking system:**

```java
public interface BankAccountAdapter {
    Money getAvailableBalance(String accountRef);
    void placeHold(String accountRef, Money amount, String referenceId);
    void commitDebit(String accountRef, Money amount, String referenceId);
    void releaseHold(String accountRef, Money amount, String referenceId);
}
```

This means:
- ✅ Bank core remains single source of truth for balances
- ✅ No data replication or synchronization needed
- ✅ Works with any CBS by implementing one adapter interface
- ✅ Cards are payment instruments for existing accounts
- ✅ Client accounts exist BEFORE card issuance

## Architecture

```
┌──────────────────────────────────────────────────────┐
│        Bank's Core Banking System (CBS)              │
│   Fineract / T24 / Mambu / FLEXCUBE / Custom         │
│                                                      │
│   • Authoritative balances                           │
│   • Transaction ledger                               │
│   • Client accounts                                  │
└──────────────────────────────────────────────────────┘
                        ▲
                        │ BankAccountAdapter
                        │ (getBalance, placeHold, commitDebit, releaseHold)
                        │
┌───────────────────────┴───────────────────────────────┐
│              Card Engine (PostgreSQL)                 │
│                                                       │
│  ┌────────────────────────────────────────────────┐   │
│  │  Card Network Webhook Controller               │   │
│  │  (Authorization, Clearing, Reversal webhooks)  │   │
│  └────────────────────────────────────────────────┘   │
│                        │                              │
│  ┌─────────────────────┴─────────────────────────┐    │
│  │   Bank Services                               │    │
│  │   • BankCardIssuanceService                   │    │
│  │   • BankAuthorizationService                  │    │
│  │   • BankSettlementService                     │    │
│  └───────────────────────────────────────────────┘    │
│                        │                              │
│  ┌─────────────────────┴─────────────────────────┐    │
│  │   Domain Entities (PostgreSQL)                │    │
│  │   • Cards                                     │    │
│  │   • BankAccountMapping (card → bank account)  │    │
│  │   • Authorizations (tracking only)            │    │
│  │   • Rules (limits, MCC blocks)                │    │
│  └───────────────────────────────────────────────┘    │
└───────────────────────────────────────────────────────┘
                        ▲
                        │ Webhook callbacks
                        │
┌───────────────────────┴────────────────────────────────┐
│        Card Processor / Network                        │
│     (Marqeta, Lithic, Galileo, Stripe Issuing)         │
└────────────────────────────────────────────────────────┘
```

See [docs/BANK_INTEGRATION.md](docs/BANK_INTEGRATION.md) for detailed bank integration documentation.

## Key Features

### 1. Bank-Centric Authorization Flow

```
1. Card network sends authorization webhook
2. Validate card state (active, not expired, belongs to bank client)
3. Run rules engine (limits, MCC blocking, velocity)
4. Check balance in BANK CORE via BankAccountAdapter
5. Place authorization hold in BANK CORE (not locally)
6. Record authorization reference locally (for tracking)
7. Return APPROVED or DECLINED to processor
```

**Key principles**:
- ✅ Balance check happens in bank core, not local database
- ✅ Authorization holds placed in bank's ledger
- ✅ Idempotent - duplicate requests return cached result
- ✅ Real-time - balance checks hit bank core synchronously

### 2. Bank-Centric Settlement & Clearing

```
1. Card processor sends clearing webhook (1-3 days after authorization)
2. Lookup authorization reference
3. Commit debit in BANK CORE via BankAccountAdapter
4. Bank core releases hold and debits the account
5. Update local authorization status to CLEARED
```

**Supports**:
- Full clearing (settle entire authorization)
- Partial clearing (settle less than authorized)
- Authorization release (cancel hold without settling)
- Reversals (refunds posted to bank core)

**Bank core performs the actual debit** - Card Engine coordinates only.

### 3. Bank Account Mapping

Cards are **payment instruments** mapped to existing bank accounts:

```java
@Entity
public class BankAccountMapping {
    private String cardId;           // Card Engine's card ID
    private String bankClientRef;    // Bank's client ID (pre-existing)
    private String bankAccountRef;   // Bank's account ID (pre-existing)
    private String bankCoreType;     // "Fineract", "T24", etc.
}
```

**Properties**:
- Immutable once created
- Client accounts must exist BEFORE card issuance
- One card maps to one bank account
- Multiple cards can map to same bank account

### 4. Rules Engine

Built-in authorization rules:
- **Transaction Limit** - Max amount per transaction
- **Daily Spend Limit** - Max spend per day per card
- **MCC Blocking** - Block merchant categories (gambling, etc.)
- **Velocity** - Max transactions per minute

**Extensible**: Implement `Rule` interface to add custom rules.

Rules run BEFORE checking balance in bank core to fail fast on policy violations.

### 5. Supported Bank Core Systems

| Bank Core | Adapter | Authorization Hold Support |
|-----------|---------|---------------------------|
| Apache Fineract | `FineractBankAccountAdapter` | Shadow journal entries |
| Temenos T24 | Implement `BankAccountAdapter` | Native hold support |
| Mambu | Implement `BankAccountAdapter` | Native hold support |
| Oracle FLEXCUBE | Implement `BankAccountAdapter` | Native hold support |
| Custom CBS | Implement `BankAccountAdapter` | Varies by system |

See [docs/BANK_INTEGRATION.md](docs/BANK_INTEGRATION.md) for integration patterns.

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker & Docker Compose (for PostgreSQL)

### 1. Start PostgreSQL

```bash
docker-compose up -d
```

### 2. Run the Application

```bash
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

### 3. View API Documentation

Open your browser to: `http://localhost:8080/swagger-ui.html`

## Example Usage

### Complete Bank-Centric Transaction Flow

```bash
# PREREQUISITE: Bank client and account already exist in your core banking system
# Example: Client ID = "CLIENT_001" with Savings Account = "SA_12345" with $1000 balance

# 1. Issue a card for existing bank account
curl -X POST http://localhost:8080/api/v1/bank/cards \
  -H "Content-Type: application/json" \
  -d '{
    "bankClientRef": "CLIENT_001",
    "bankAccountRef": "SA_12345",
    "cardholderName": "Alice Johnson",
    "expirationDate": "2027-12-31",
    "issuedBy": "bank-admin"
  }'

# Response: { "cardId": "card-abc...", "state": "FROZEN", ... }

# 2. Activate the card
curl -X POST "http://localhost:8080/api/v1/bank/cards/card-abc.../activate"

# Response: { "cardId": "card-abc...", "state": "ACTIVE" }

# 3. Card network sends authorization webhook (when customer swipes card)
# This happens automatically from the card processor - NOT a manual API call
# Card Engine checks balance in YOUR bank core and places hold there

# For testing, you can simulate:
curl -X POST http://localhost:8080/api/v1/webhooks/processor/sample/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "processorTransactionId": "proc-tx-001",
    "cardToken": "card-abc...",
    "amount": 75.50,
    "currency": "USD",
    "merchantName": "Coffee Shop",
    "merchantCategoryCode": "5814",
    "idempotencyKey": "idem-001"
  }'

# Response: { "status": "APPROVED", "authorizationId": "auth-123..." }
# Behind the scenes:
#   - Card Engine checked balance in YOUR bank core
#   - Hold of $75.50 placed in YOUR bank's ledger
#   - Card Engine stored reference to authorization

# 4. Card network sends clearing webhook (1-3 days later)
curl -X POST http://localhost:8080/api/v1/webhooks/processor/sample/clear \
  -H "Content-Type: application/json" \
  -d '{
    "processorTransactionId": "proc-tx-001",
    "clearingAmount": 75.50,
    "clearingCurrency": "USD"
  }'

# Response: { "status": "CLEARED" }
# Behind the scenes:
#   - Hold released in YOUR bank core
#   - Debit of $75.50 committed in YOUR bank's ledger
#   - Account balance in bank core now $924.50

# 5. Check balance in YOUR bank core (Card Engine doesn't store it)
# Use your bank's core banking API - Card Engine doesn't own balances
```

**Key points**:
- ✅ Client account exists in YOUR bank core BEFORE card issuance
- ✅ Balance checks hit YOUR bank core in real-time
- ✅ Authorization holds placed in YOUR bank's ledger
- ✅ Card Engine coordinates, bank core owns the money

See [docs/BANK_INTEGRATION.md](docs/BANK_INTEGRATION.md) for complete integration guide.

## Testing

### Run All Tests

```bash
mvn test
```

### Key Test Suites

- `AuthorizationServiceTest` - Full authorization flow
- `SettlementServiceTest` - Clearing and reversals
- `AccountAbstractionTest` - Account interface compliance

## Project Structure

```
card-engine/
├── src/main/java/com/cardengine/
│   ├── bank/              # Bank core integration layer
│   │   ├── BankAccountAdapter.java           # Generic bank adapter interface
│   │   ├── BankAccountMapping.java           # Card-to-account mapping
│   │   ├── BankCardIssuanceService.java      # Issue cards for bank accounts
│   │   ├── BankAuthorizationService.java     # Authorize with bank core
│   │   ├── BankSettlementService.java        # Settle with bank core
│   │   ├── fineract/
│   │   │   └── FineractBankAccountAdapter.java  # Fineract implementation
│   │   └── mock/
│   │       └── MockBankAccountAdapter.java      # Testing mock
│   ├── cards/             # Card lifecycle management
│   ├── authorization/     # Authorization entities and rules
│   ├── settlement/        # Settlement entities
│   ├── rules/             # Rules engine (limits, MCC, velocity)
│   ├── providers/         # Card processor adapters
│   │   ├── processor/     # SampleProcessor webhook adapter
│   │   └── fineract/      # Fineract API client
│   ├── api/               # REST API controllers
│   └── common/            # Shared types (Money, Currency)
├── src/test/java/com/cardengine/
│   └── bank/              # Bank integration tests
├── docs/
│   ├── BANK_INTEGRATION.md     # Bank core integration guide
│   ├── FINERACT_INTEGRATION.md # Fineract setup guide
│   └── PROCESSOR_INTEGRATION.md # Processor integration guide
└── docker-compose.yml     # PostgreSQL setup
```

## Extension Points

### Integrate a New Bank Core System

To integrate your core banking system (Temenos T24, Mambu, Oracle FLEXCUBE, etc.):

```java
@Component
public class T24BankAccountAdapter implements BankAccountAdapter {

    private final T24ApiClient t24Client;

    @Override
    public Money getAvailableBalance(String accountRef) {
        // Call T24 API to get current available balance
        T24Account account = t24Client.getAccount(accountRef);
        return Money.of(account.getAvailableBalance(), Currency.USD);
    }

    @Override
    public void placeHold(String accountRef, Money amount, String referenceId) {
        // Check idempotency (has this referenceId been processed?)
        if (holdExists(referenceId)) {
            return;  // Already processed
        }

        // T24 has native authorization hold support
        t24Client.createAuthorizationHold(accountRef, amount, referenceId);
    }

    @Override
    public void commitDebit(String accountRef, Money amount, String referenceId) {
        // Convert hold to final debit
        t24Client.commitHold(accountRef, referenceId, amount);
    }

    @Override
    public void releaseHold(String accountRef, Money amount, String referenceId) {
        // Cancel hold without debiting
        t24Client.reverseHold(accountRef, referenceId);
    }

    @Override
    public String getAdapterName() {
        return "T24";
    }
}
```

**NO business logic in adapters** - only API translation.

See [docs/BANK_INTEGRATION.md](docs/BANK_INTEGRATION.md) for authorization hold patterns.

### Add a New Authorization Rule

```java
@Component
public class GeofenceRule implements Rule {

    @Override
    public RuleResult evaluate(AuthorizationRequest request) {
        // Block transactions outside allowed countries
        if (!allowedCountries.contains(request.getMerchantCountry())) {
            return RuleResult.decline("Transaction blocked: country not allowed");
        }
        return RuleResult.approve();
    }

    @Override
    public String getRuleName() {
        return "GeofenceRule";
    }
}
```

Rules are auto-discovered by Spring and run before bank core balance checks.

### Integrate a New Card Processor

```java
@Component
public class MarqetaProcessorAdapter {

    @PostMapping("/webhooks/marqeta/authorize")
    public MarqetaResponse handleAuthorization(@RequestBody MarqetaWebhook webhook) {
        // 1. Map Marqeta webhook to internal AuthorizationRequest
        AuthorizationRequest request = mapToInternalFormat(webhook);

        // 2. Call core authorization service (runs rules, checks bank core)
        AuthorizationResponse response = bankAuthorizationService.authorize(request);

        // 3. Store ID mapping for later clearing
        storeMapping(webhook.getMarqetaTransactionId(), response.getAuthorizationId());

        // 4. Translate response to Marqeta format
        return mapToMarqetaFormat(response);
    }
}
```

**Adapters only translate** - never contain authorization logic.

## Configuration

Key configuration in `application.yml`:

```yaml
card-engine:
  # Bank Core Integration
  bank:
    adapter: fineract  # Options: fineract, mock, custom

  # Apache Fineract Configuration (if using Fineract adapter)
  fineract:
    enabled: true
    base-url: http://your-fineract:8443/fineract-provider/api/v1
    tenant: default
    username: mifos
    password: password
    card-auth-holds-gl-account-id: 1000  # GL account for shadow holds

  # Card Processor Configuration
  processor:
    active: sample  # Options: mock, sample, marqeta, lithic
    sample:
      enabled: true
      webhook-secret: your-webhook-secret

  # Authorization Rules
  rules:
    daily-limit-default: 5000.00         # Default daily spending limit
    transaction-limit-default: 1000.00   # Default per-transaction limit
    velocity-max-per-minute: 5           # Max transactions per minute
```

## Integrations

Card Engine provides production-ready adapters for bank cores and card processors.

### Bank Core Integration

#### Apache Fineract (Reference Implementation)

Apache Fineract is an open-source core banking platform used by banks worldwide. Card Engine includes `FineractBankAccountAdapter` as a reference implementation.

**How It Works:**

The adapter implements `BankAccountAdapter` and translates card operations to Fineract API calls:

```java
@Component
public class FineractBankAccountAdapter implements BankAccountAdapter {

    @Override
    public Money getAvailableBalance(String accountRef) {
        // Query Fineract savings account
        Long savingsAccountId = parseAccountRef(accountRef);
        return fineractClient.getAccountBalance(savingsAccountId);
    }

    @Override
    public void placeHold(String accountRef, Money amount, String referenceId) {
        // Shadow journal entry workaround for authorization holds
        // DEBIT savings account → CREDIT CARD_AUTH_HOLDS GL account
        fineractClient.createJournalEntry(/* ... */);
        // Track hold in fineract_auth_holds table
    }
}
```

**Authorization Hold Workaround:**

Fineract doesn't natively support card authorization holds. We use **shadow journal entries**:

1. **placeHold()** - Create journal entry debiting the account, crediting CARD_AUTH_HOLDS GL account
2. **commitDebit()** - Reverse shadow entry, create actual debit transaction
3. **releaseHold()** - Reverse shadow entry (cancel hold)

This ensures:
- ✅ Funds unavailable during hold period
- ✅ Fineract ledger remains balanced
- ✅ All movements auditable in Fineract
- ✅ No duplicate debits

**Setup:**

1. Configure Fineract in `application.yml`:
```yaml
card-engine:
  bank:
    adapter: fineract
  fineract:
    base-url: http://your-fineract:8443/fineract-provider/api/v1
    tenant: default
    username: mifos
    password: password
    card-auth-holds-gl-account-id: 1000
```

2. Create CARD_AUTH_HOLDS GL account in Fineract (one-time)

3. Ensure client savings accounts exist before issuing cards

See [docs/BANK_INTEGRATION.md](docs/BANK_INTEGRATION.md) for complete integration guide.

#### Other Bank Cores

The same `BankAccountAdapter` interface works with:
- **Temenos T24** - Implement adapter using T24 APIs
- **Mambu** - Implement adapter using Mambu REST API
- **Oracle FLEXCUBE** - Implement adapter using FLEXCUBE APIs
- **Custom CBS** - Implement adapter for your proprietary system

See [docs/BANK_INTEGRATION.md](docs/BANK_INTEGRATION.md) for integration patterns and authorization hold strategies.

### Card Processor Integration

#### SampleProcessor (Reference Implementation)

The `SampleProcessorAdapter` demonstrates real card network/processor webhook integration.

**How It Works:**

1. **Authorization Webhook**:
   - Card network sends authorization request (customer swipes card)
   - Adapter translates to `AuthorizationRequest`
   - `BankAuthorizationService` runs rules and checks balance in bank core
   - Adapter returns APPROVED/DECLINED to processor

2. **Clearing Webhook**:
   - Processor notifies settlement (1-3 days later)
   - Adapter looks up authorization ID
   - `BankSettlementService` commits debit in bank core

3. **Reversal Webhook**:
   - Processor notifies refund/cancellation
   - Settlement service releases hold in bank core

**Key Features:**
- ✅ ID mapping between processor and internal authorization IDs
- ✅ Idempotent webhook processing
- ✅ NO business logic - adapters only translate

**Webhook Endpoints:**
```
POST /api/v1/webhooks/processor/sample/authorize
POST /api/v1/webhooks/processor/sample/clear
POST /api/v1/webhooks/processor/sample/reverse
```

**Configuration:**
```yaml
card-engine:
  processor:
    active: sample
    sample:
      enabled: true
      webhook-secret: your-webhook-secret
```

#### Integrating Other Processors

To integrate Marqeta, Lithic, Galileo, Stripe Issuing, etc.:

1. Implement webhook DTOs matching processor format
2. Create webhook controller receiving processor callbacks
3. Map processor IDs to internal authorization IDs
4. Call `BankAuthorizationService` and `BankSettlementService`
5. Translate responses to processor format
6. Store ID mappings for clearing correlation

**Rule**: Adapters translate only. Never add authorization logic.

See `SampleProcessorAdapter` as reference: [src/main/java/com/cardengine/providers/processor/](src/main/java/com/cardengine/providers/processor/)

## Roadmap

### MVP (Current)
- ✅ **Bank core integration architecture** - Generic adapter pattern for any CBS
- ✅ **BankAccountAdapter interface** - Vendor-neutral bank integration
- ✅ **Apache Fineract integration** - Reference implementation with shadow holds
- ✅ **MockBankAccountAdapter** - Testing without real bank core
- ✅ **Bank-centric authorization** - Balance checks in bank core, not locally
- ✅ **Bank-centric settlement** - Commits/releases in bank ledger
- ✅ **Card-to-account mapping** - Immutable card → existing bank account
- ✅ **Card processor webhooks** - SampleProcessor reference implementation
- ✅ **Rules engine** - Limits, MCC blocking, velocity
- ✅ **Comprehensive tests** - Bank integration test suite

### Future Enhancements
- [ ] Additional bank adapters (Temenos T24, Mambu, Oracle FLEXCUBE)
- [ ] Additional processor integrations (Marqeta, Lithic, Galileo, Stripe Issuing)
- [ ] Multi-currency support with FX integration
- [ ] Webhook events for real-time notifications
- [ ] Advanced fraud detection and ML-based rules
- [ ] Multi-card per bank account support
- [ ] Granular spend controls (per merchant, per category, per time)
- [ ] Reporting and analytics APIs
- [ ] Admin dashboard for card operations
- [ ] 3DS authentication integration
- [ ] Dispute management workflows

## Production Readiness Checklist

Before using in production, you must:

- [ ] Implement PCI DSS compliance for card data
- [ ] Add PAN tokenization (never store full card numbers)
- [ ] Encrypt sensitive database fields
- [ ] Implement proper authentication and authorization
- [ ] Add rate limiting and DDoS protection
- [ ] Set up monitoring and alerting
- [ ] Implement proper key management
- [ ] Add comprehensive audit logging
- [ ] Perform security penetration testing
- [ ] Obtain necessary financial licenses
- [ ] Partner with licensed card issuer
- [ ] Integrate with real card network/processor

## Legal Disclaimer

**IMPORTANT**: This software is provided for educational and infrastructure purposes only.

This software:
- Is an integration layer for banks with existing licenses
- Does NOT provide banking services
- Does NOT hold customer funds (bank core does)
- Does NOT process card transactions without processor partnerships
- REQUIRES appropriate banking and card issuing licenses
- Bank must have existing partnership with card issuer/processor

**For banks deploying this:**
- Ensure you have appropriate banking licenses
- Partner with licensed card issuer or processor
- Obtain PCI DSS certification for card data handling
- Comply with card network rules (Visa/Mastercard/etc.)
- Maintain KYC/AML compliance in your core banking system
- Consult legal and compliance experts

**This is infrastructure software for licensed financial institutions only.**

## Contributing

Contributions are welcome! This is an open-source foundation project.

Areas where contributions would be valuable:
- Additional bank core adapters (T24, Mambu, FLEXCUBE, etc.)
- Additional card processor integrations (Marqeta, Lithic, Galileo)
- More sophisticated fraud and authorization rules
- Performance optimizations
- Multi-currency and FX support
- Documentation improvements

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

## Support

- **Bank Integration Guide**: [docs/BANK_INTEGRATION.md](docs/BANK_INTEGRATION.md)
- **Fineract Setup**: [docs/FINERACT_INTEGRATION.md](docs/FINERACT_INTEGRATION.md)
- **Processor Integration**: [docs/PROCESSOR_INTEGRATION.md](docs/PROCESSOR_INTEGRATION.md)
- **Issues**: Open an issue on GitHub
- **Discussions**: Use GitHub Discussions for questions

## Acknowledgments

This project provides bank-agnostic card infrastructure for financial institutions. It enables banks to offer card programs while maintaining their core banking system as the source of truth.

**Built with**: Java 21, Spring Boot, PostgreSQL, and a commitment to clean architecture.

**Design philosophy**: Bank core is authoritative. Card Engine coordinates, never owns balances.

---

**Note**: Card Engine is infrastructure software for banks and licensed financial institutions. It requires technical expertise and appropriate licenses to deploy and operate.
