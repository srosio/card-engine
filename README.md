# Card Engine

**Production-grade open-source card orchestration platform**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## What is Card Engine?

Card Engine is an **issuer-agnostic, account-agnostic card orchestration platform** that handles card authorization, settlement, and ledger logic while delegating compliance and card issuing to external providers.

### What Card Engine Is

✅ **Infrastructure for card programs** - Handle auth, settlement, ledger
✅ **Account abstraction layer** - One card, any funding source
✅ **Double-entry ledger** - Immutable financial audit trail
✅ **Rules engine** - Spending limits, MCC blocking, velocity
✅ **Provider adapters** - Integrate with any card processor

### What Card Engine Is NOT

❌ **Not a bank** - No money transmission, no deposits
❌ **Not a card issuer** - Requires partnership with licensed issuer
❌ **Not a payment processor** - Integrates with existing processors
❌ **Not a compliance platform** - KYC/AML handled externally
❌ **Not a crypto wallet** - Just coordinates funding sources

## The Problem

Building a card program today requires:
1. Integrating with a card issuer processor (Marqeta, Lithic, Galileo)
2. Managing complex authorization and settlement flows
3. Maintaining accurate ledger accounting
4. Supporting multiple funding sources (bank accounts, crypto, prepaid)
5. Implementing fraud rules and spending controls

Most companies end up building tightly coupled systems where **card logic is hardcoded to one account type**. Switching from a prepaid ledger to a bank account requires rewriting authorization logic.

## The Solution

Card Engine solves this with **account abstraction**:

```
┌─────────────────────────────────────────────────────┐
│                    ONE CARD                          │
└─────────────────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┬──────────────┐
        │               │               │              │
        ▼               ▼               ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────┐ ┌────────────┐
│ Internal     │ │ Fiat Bank    │ │Stablecoin│ │ Custodial  │
│ Ledger       │ │ Account      │ │ Wallet   │ │ Account    │
└──────────────┘ └──────────────┘ └──────────┘ └────────────┘
```

**All account types implement the same interface:**

```java
public interface Account {
    Money getBalance();
    void reserve(Money amount, String authId);
    void commit(Money amount, String authId);
    void release(Money amount, String authId);
}
```

This means:
- ✅ Switch funding sources without code changes
- ✅ Mix account types in same platform
- ✅ Add new account types by implementing one interface
- ✅ Authorization logic doesn't know or care about account internals

## Architecture

```
External Systems (Card Networks, Banks, Custody)
                    │
                    ▼
         ┌─────────────────────┐
         │ Provider Adapters   │
         └─────────────────────┘
                    │
                    ▼
         ┌─────────────────────┐
         │   REST API Layer    │
         └─────────────────────┘
                    │
    ┌───────────────┼───────────────┐
    ▼               ▼               ▼
┌─────────┐  ┌─────────────┐  ┌──────────┐
│  Auth   │  │ Settlement  │  │  Ledger  │
│ Service │  │  Service    │  │ Service  │
└─────────┘  └─────────────┘  └──────────┘
    │               │               │
    └───────────────┼───────────────┘
                    ▼
         ┌─────────────────────┐
         │   Domain Layer      │
         │  Cards │ Accounts   │
         └─────────────────────┘
                    │
                    ▼
         ┌─────────────────────┐
         │    PostgreSQL       │
         └─────────────────────┘
```

See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for detailed architecture documentation.

## Key Features

### 1. Authorization Flow

```
1. Validate card state (active, not expired)
2. Run rules engine (limits, MCC blocking, velocity)
3. Reserve funds from backing account
4. Record authorization hold in ledger
5. Return APPROVED or DECLINED
```

**Idempotent** - Duplicate requests return cached result
**Real-time** - Balance checks happen at authorization time
**Flexible rules** - Easy to add custom authorization rules

### 2. Settlement & Clearing

```
1. Receive clearing event from processor
2. Validate authorization exists and is approved
3. Commit funds from account
4. Record clearing in ledger
5. Update authorization status
```

**Supports**:
- Full clearing (settle entire authorization)
- Partial clearing (settle less than authorized)
- Authorization release (cancel without settling)
- Reversals (refunds)

### 3. Double-Entry Ledger

Every financial operation is recorded as an **immutable ledger entry**:

- `AUTH_HOLD` - Reserve funds during authorization
- `AUTH_RELEASE` - Release reserved funds
- `CLEARING_COMMIT` - Commit funds during settlement
- `REVERSAL` - Refund a cleared transaction
- `DEPOSIT` - Add funds to account
- `WITHDRAWAL` - Remove funds from account

**Properties**:
- Append-only (entries never updated or deleted)
- Full audit trail
- Reconciliation ready
- Idempotent (duplicate operations prevented)

### 4. Rules Engine

Built-in rules:
- **Transaction Limit** - Max amount per transaction
- **Daily Spend Limit** - Max spend per day per card
- **MCC Blocking** - Block merchant categories (gambling, etc.)
- **Velocity** - Max transactions per minute

**Extensible**: Implement `Rule` interface to add custom rules.

### 5. Account Types (MVP)

| Account Type | Description | Use Case |
|--------------|-------------|----------|
| `INTERNAL_LEDGER` | Managed within card engine | Prepaid card programs |
| `FIAT_WALLET` | Mock bank account | Traditional debit cards |
| `STABLECOIN` | Mock crypto account | Crypto-backed cards |
| `EXTERNAL_CUSTODIAL` | Read-only external | View-only integrations |

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

### Complete Transaction Flow

```bash
# 1. Create an account
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "ownerId": "user-123",
    "accountType": "INTERNAL_LEDGER",
    "currency": "USD",
    "initialBalance": 1000.00
  }'

# Response: { "accountId": "acc-xyz...", "balance": { "amount": 1000.00, "currency": "USD" } }

# 2. Issue a card
curl -X POST http://localhost:8080/api/v1/cards \
  -H "Content-Type: application/json" \
  -d '{
    "cardholderName": "Alice Johnson",
    "last4": "4242",
    "expirationDate": "2027-12-31",
    "fundingAccountId": "acc-xyz...",
    "ownerId": "user-123"
  }'

# Response: { "cardId": "card-abc...", "state": "ACTIVE", ... }

# 3. Authorize a transaction
curl -X POST http://localhost:8080/api/v1/authorizations \
  -H "Content-Type: application/json" \
  -d '{
    "cardId": "card-abc...",
    "amount": 75.50,
    "currency": "USD",
    "merchantName": "Coffee Shop",
    "merchantCategoryCode": "5814"
  }'

# Response: { "authorizationId": "auth-123...", "status": "APPROVED" }

# 4. Clear the transaction (settlement)
curl -X POST "http://localhost:8080/api/v1/settlement/clear/auth-123...?amount=75.50&currency=USD"

# 5. View ledger
curl http://localhost:8080/api/v1/accounts/acc-xyz.../ledger
```

See [examples/sample-flows/](examples/sample-flows/) for more complete examples including:
- Different account types
- Declined transactions
- Reversal flows
- Partial clearing

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
│   ├── accounts/          # Account abstraction layer
│   │   ├── Account.java
│   │   ├── InternalLedgerAccount.java
│   │   ├── MockFiatAccount.java
│   │   └── MockStablecoinAccount.java
│   ├── cards/             # Card lifecycle management
│   ├── ledger/            # Double-entry ledger
│   ├── authorization/     # Authorization flow
│   ├── settlement/        # Clearing and settlement
│   ├── rules/             # Rules engine
│   ├── providers/         # External provider adapters
│   ├── api/               # REST API controllers
│   └── common/            # Shared types and utilities
├── examples/
│   └── sample-flows/      # HTTP request examples
├── docs/
│   └── ARCHITECTURE.md    # Detailed architecture
└── docker-compose.yml     # PostgreSQL setup
```

## Extension Points

### Add a New Account Type

```java
@Entity
@DiscriminatorValue("MY_ACCOUNT_TYPE")
public class MyCustomAccount extends BaseAccount {

    public MyCustomAccount(String ownerId, Money initialBalance) {
        super(ownerId, initialBalance, AccountType.MY_CUSTOM);
    }

    @Override
    public AccountType getAccountType() {
        return AccountType.MY_CUSTOM;
    }

    // Optionally override reserve/commit/release for custom logic
}
```

### Add a New Rule

```java
@Component
public class MyCustomRule implements Rule {

    @Override
    public RuleResult evaluate(AuthorizationRequest request) {
        // Your custom logic
        if (shouldDecline) {
            return RuleResult.decline("Custom decline reason");
        }
        return RuleResult.approve();
    }

    @Override
    public String getRuleName() {
        return "MyCustomRule";
    }
}
```

### Add a Provider Integration

```java
@Component
public class RealCardProcessor implements CardProcessorAdapter {

    @Override
    public void sendAuthorizationResponse(AuthorizationResponse response) {
        // Call real processor API
        processorClient.send(response);
    }

    @Override
    public String getProcessorName() {
        return "RealProcessor";
    }
}
```

## Configuration

Key configuration in `application.yml`:

```yaml
card-engine:
  rules:
    daily-limit-default: 5000.00      # Default daily spending limit
    transaction-limit-default: 1000.00 # Default per-transaction limit
    velocity-max-per-minute: 5         # Max transactions per minute

  # Apache Fineract Integration
  fineract:
    enabled: false
    base-url: http://localhost:8443/fineract-provider/api/v1
    tenant: default
    username: mifos
    password: password
    card-auth-holds-gl-account-id: 1000

  # Card Processor Configuration
  processor:
    active: mock  # Options: mock, sample
```

## Integrations

Card Engine provides production adapters for real-world integrations.

### Apache Fineract Integration

**Fineract** is used as the authoritative ledger system for account balances and transactions.

#### What is Fineract?

Apache Fineract is an open-source core banking platform used by financial institutions worldwide. When integrated with Card Engine, Fineract becomes the source of truth for:
- Account balances
- Transaction history
- Ledger entries

#### How It Works

The `FineractAccountAdapter` implements the `Account` interface and translates card operations into Fineract API calls:

**Authorization Hold Workaround:**

Since Fineract doesn't natively support card-style authorization holds, we use a shadow transaction approach:

1. **RESERVE** (Authorization):
   - Create journal entry: DEBIT user's savings account → CREDIT CARD_AUTH_HOLDS GL account
   - Funds become unavailable but aren't removed from the account
   - Hold reference stored in `fineract_auth_holds` table

2. **COMMIT** (Clearing):
   - Reverse the hold journal entry (returns funds to available balance)
   - Make actual debit from savings account
   - Mark hold as COMMITTED

3. **RELEASE** (Expiry/Cancellation):
   - Reverse the hold journal entry
   - Mark hold as RELEASED

This ensures:
- ✅ Fineract ledger remains balanced
- ✅ Funds are properly reserved during authorization
- ✅ All movements are auditable in Fineract
- ✅ No duplicate debits on clearing

#### Setup

1. **Configure Fineract connection in `application.yml`:**

```yaml
card-engine:
  fineract:
    enabled: true
    base-url: http://your-fineract-instance:8443/fineract-provider/api/v1
    tenant: your-tenant
    username: your-username
    password: your-password
    card-auth-holds-gl-account-id: 1000  # GL account for holding funds
```

2. **Create the CARD_AUTH_HOLDS GL account in Fineract** (one-time setup)

3. **Use FineractAccountAdapter for new accounts:**

```java
FineractAccountAdapter account = new FineractAccountAdapter(
    fineractClient,
    holdRepository,
    accountId,
    fineractSavingsAccountId,  // Link to Fineract account
    Currency.USD,
    cardAuthHoldsGLAccountId
);
```

See `docs/FINERACT_INTEGRATION.md` for detailed setup instructions.

### SampleProcessor Integration

**SampleProcessor** demonstrates real card processor webhook integration.

#### How It Works

The `SampleProcessorAdapter` handles webhook callbacks from card processors:

1. **Authorization Webhook**:
   - Processor sends real-time authorization request
   - Adapter translates to internal `AuthorizationRequest`
   - Core orchestration processes (rules, balance checks)
   - Response sent back to processor (APPROVED/DECLINED)

2. **Clearing Webhook**:
   - Processor notifies when transaction settles (1-3 days later)
   - Adapter looks up internal authorization ID
   - Settlement service commits funds

3. **Reversal Webhook**:
   - Processor notifies of refund/cancellation
   - Adapter processes reversal through settlement service

#### Key Features

- **ID Mapping**: Processor transaction IDs mapped to internal authorization IDs
- **Idempotency**: Duplicate webhooks handled safely
- **No Business Logic**: Adapter only translates; core handles all logic

#### Webhook Endpoints

```
POST /api/v1/webhooks/processor/sample/authorize
POST /api/v1/webhooks/processor/sample/clear
POST /api/v1/webhooks/processor/sample/reverse
```

#### Configuration

```yaml
card-engine:
  processor:
    active: sample
    sample:
      enabled: true
      webhook-secret: your-webhook-secret
```

#### Testing

See `SampleProcessorAdapterTest` for webhook flow examples.

### Adding Your Own Processor

To integrate a new card processor (Marqeta, Lithic, etc.):

1. **Implement webhook DTOs** matching processor's format
2. **Create adapter class** that:
   - Receives processor webhooks
   - Maps processor IDs to internal IDs
   - Translates to `AuthorizationRequest`/`ClearingRequest`
   - Calls core orchestration services
3. **Add webhook controller** endpoints
4. **Store ID mappings** in database
5. **Preserve idempotency keys** from processor

**Key Rule**: Adapters only translate. Never put business logic in adapters.

See `com.cardengine.providers.processor.SampleProcessorAdapter` as reference implementation.

## Roadmap

### MVP (Current)
- ✅ Account abstraction with 3 types
- ✅ Authorization and settlement flows
- ✅ Double-entry ledger
- ✅ Basic rules engine
- ✅ REST API
- ✅ Mock provider adapters
- ✅ **Apache Fineract integration (authoritative ledger)**
- ✅ **Real processor adapter (SampleProcessor with webhooks)**
- ✅ **Authorization hold workaround for Fineract**

### Future Enhancements
- [ ] Multi-currency support with FX
- [ ] Webhook events for internal notifications
- [ ] Advanced fraud detection
- [ ] Multi-card per account
- [ ] Spend controls per merchant/category
- [ ] Reporting and analytics APIs
- [ ] Admin dashboard
- [ ] Additional processor integrations (Marqeta, Lithic, Stripe Issuing)

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
- Does NOT provide financial services
- Does NOT hold customer funds
- Does NOT process real card transactions without external integrations
- REQUIRES appropriate licensing to operate in production
- REQUIRES partnership with licensed card issuers and processors

Operating a card program requires:
- Money transmitter licenses (varies by jurisdiction)
- Partnership with a licensed bank or card issuer
- PCI DSS certification for handling card data
- Compliance with Visa/Mastercard network rules
- KYC/AML compliance programs

**Consult legal and compliance experts before using this in production.**

## Contributing

Contributions are welcome! This is an open-source foundation project.

Areas where contributions would be valuable:
- Additional account type implementations
- More sophisticated fraud rules
- Real provider integrations
- Performance optimizations
- Documentation improvements

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

## Support

- **Documentation**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- **Examples**: [examples/sample-flows/](examples/sample-flows/)
- **Issues**: Open an issue on GitHub
- **Discussions**: Use GitHub Discussions for questions

## Acknowledgments

This project is inspired by production card infrastructure at fintech companies, but is built from scratch as an open-source foundation for the community.

**Built with**: Java 21, Spring Boot, PostgreSQL, and a commitment to clean architecture.

---

**Note**: Card Engine is infrastructure software designed for engineers building card programs. It is not end-user software and requires technical expertise to deploy and operate.
