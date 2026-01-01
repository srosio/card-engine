# Card Engine Architecture

## Overview

The Card Engine is a modular monolith designed with clean architecture principles. It separates concerns into distinct layers while maintaining a single deployable unit for simplicity in the MVP phase.

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     External Systems                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │Card Networks │  │  FX Provider │  │ Bank/Custody │      │
│  │(Visa, MC)    │  │              │  │   Partners   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Provider Adapters                         │
│  ┌──────────────┐  ┌──────────────┐                         │
│  │Card Processor│  │  FX Provider │                         │
│  │   Adapter    │  │   Adapter    │                         │
│  └──────────────┘  └──────────────┘                         │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      REST API Layer                          │
│  ┌──────────┐  ┌──────────┐  ┌─────────────┐               │
│  │ Accounts │  │  Cards   │  │Authorizations│               │
│  └──────────┘  └──────────┘  └─────────────┘               │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Business Logic Layer                       │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           Authorization Service                       │  │
│  │  1. Validate card state                              │  │
│  │  2. Run rules engine                                 │  │
│  │  3. Reserve funds from account                       │  │
│  │  4. Record in ledger                                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           Settlement Service                          │  │
│  │  1. Validate authorization                           │  │
│  │  2. Commit/release funds                             │  │
│  │  3. Record in ledger                                 │  │
│  │  4. Update authorization status                      │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Rules Engine │  │Ledger Service│  │Account Service│     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     Domain Layer                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  Card    │  │ Account  │  │  Ledger  │  │   Auth   │   │
│  │ Entity   │  │Interface │  │  Entry   │  │  Entity  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Persistence Layer                         │
│                    PostgreSQL Database                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  cards   │  │ accounts │  │  ledger  │  │   auth   │   │
│  │  table   │  │  table   │  │  entries │  │  table   │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Core Concepts

### 1. Account Abstraction

The card engine's most important architectural decision is **account abstraction**.

**Key Principle**: Cards never store money. Accounts store money. Cards only authorize access.

All account types implement a common `Account` interface with these operations:
- `getBalance()` - Available balance
- `reserve(amount, authId)` - Hold funds for authorization
- `commit(amount, authId)` - Finalize transaction
- `release(amount, authId)` - Cancel authorization
- `getCurrency()` - Account currency

This allows one card to be backed by:
- Internal prepaid ledger
- External fiat bank account
- Stablecoin wallet
- Custodial account
- Any future account type

### 2. Double-Entry Ledger

All financial operations are recorded as immutable ledger entries following double-entry accounting.

**Transaction Types**:
- `AUTH_HOLD` - Reserve funds during authorization
- `AUTH_RELEASE` - Release reserved funds
- `CLEARING_COMMIT` - Commit funds during settlement
- `REVERSAL` - Refund a cleared transaction
- `DEPOSIT` - Add funds to account
- `WITHDRAWAL` - Remove funds from account

Every entry has:
- Unique entry ID
- Transaction ID (groups related entries)
- Account ID
- Entry type (DEBIT or CREDIT)
- Amount and currency
- Transaction type
- Optional authorization/card ID
- Idempotency key
- Immutable timestamp

### 3. Authorization Flow

```
Authorization Request
        │
        ▼
┌───────────────────┐
│ Validate Card     │
│ - Active?         │
│ - Not expired?    │
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ Run Rules Engine  │
│ - Transaction limit│
│ - Daily limit     │
│ - MCC blocking    │
│ - Velocity check  │
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ Reserve Funds     │
│ from Account      │
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ Record in Ledger  │
│ (AUTH_HOLD)       │
└───────────────────┘
        │
        ▼
    APPROVED
```

### 4. Settlement Flow

```
Clearing Request
        │
        ▼
┌───────────────────┐
│ Validate Auth     │
│ - Exists?         │
│ - Approved?       │
│ - Amount valid?   │
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ Commit Funds      │
│ from Account      │
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ Record in Ledger  │
│ (CLEARING_COMMIT) │
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ Update Auth       │
│ Status = CLEARED  │
└───────────────────┘
```

## Data Flow

### Authorization Data Flow

1. **Incoming Request** → REST API receives authorization request
2. **Validation** → Card service validates card state
3. **Rules** → Rules engine evaluates transaction
4. **Account** → Account reserves funds (via abstraction)
5. **Ledger** → Immutable entry recorded
6. **Response** → APPROVED or DECLINED returned

### Settlement Data Flow

1. **Clearing Request** → REST API receives clearing/settlement
2. **Validation** → Authorization exists and in correct state
3. **Account** → Account commits reserved funds
4. **Ledger** → Clearing entry recorded
5. **Update** → Authorization status updated

## Key Design Decisions

### 1. Modular Monolith (Not Microservices)

**Why**: Simplicity for MVP. All components in one deployable unit reduces operational complexity.

**Future**: Can be split into microservices later along clear domain boundaries.

### 2. Immutable Ledger

**Why**: Financial audit trail must never be modified. All corrections are new entries.

**Impact**: Ledger entries are append-only. No UPDATE or DELETE operations.

### 3. Idempotency

**Why**: Network failures and retries are common. Must prevent duplicate charges.

**Implementation**: Every operation requires an idempotency key. Duplicate keys return cached result.

### 4. Reserve-Commit-Release Pattern

**Why**: Card authorizations and clearing happen at different times (hours or days apart).

**Implementation**:
- Authorization reserves funds (unavailable but not moved)
- Clearing commits funds (actually moved)
- Release frees funds if authorization expires

### 5. Provider Adapters

**Why**: External integrations change. Card processors, FX providers, and banks all have different APIs.

**Implementation**: Adapter interfaces allow swapping implementations without changing core logic.

## Extensibility Points

### Adding New Account Types

1. Implement `Account` interface
2. Add to account factory in `AccountService`
3. No changes to cards, authorization, or settlement logic

### Adding New Rules

1. Implement `Rule` interface
2. Spring auto-discovers and adds to rules engine
3. Rules evaluated in order

### Adding New Provider

1. Define adapter interface
2. Implement mock version
3. Swap with real integration when ready

## Database Schema

### Accounts Table
- Polymorphic (single table inheritance)
- Stores balance and reserves map
- Different discriminator for each account type

### Cards Table
- References funding account
- Tracks state and metadata
- No balance (balance in account)

### Authorizations Table
- Full authorization lifecycle
- References card and account
- Tracks status changes

### Ledger Entries Table
- Immutable audit log
- Indexed by account, transaction, auth ID
- Never updated or deleted

## Security Considerations

### In Production Would Require

1. **PCI Compliance** for card data
2. **Encryption** of sensitive fields
3. **PAN Tokenization** (full card numbers never stored)
4. **mTLS** for external integrations
5. **Rate Limiting** on APIs
6. **Fraud Detection** beyond basic rules
7. **Audit Logging** of all operations

## Performance Characteristics

### Bottlenecks

- Database writes (every operation writes to ledger)
- Rules engine (runs on every authorization)
- Account reserve map (in-memory, can grow large)

### Optimizations for Production

- Read replicas for balance queries
- Cache account data
- Async ledger writes (with guarantees)
- Batch settlement processing
