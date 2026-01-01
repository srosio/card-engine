# Getting Started with Card Engine

This guide will walk you through setting up and running your first card transaction using Card Engine.

## Prerequisites

Before you begin, ensure you have:
- Java 21 or higher installed
- Maven 3.8 or higher installed
- Docker and Docker Compose installed
- A terminal/command prompt
- An HTTP client (curl, Postman, or IntelliJ HTTP Client)

## Step 1: Clone and Setup

```bash
git clone <repository-url>
cd card-engine
```

## Step 2: Start PostgreSQL

```bash
docker-compose up -d
```

Verify PostgreSQL is running:
```bash
docker-compose ps
```

## Step 3: Build the Project

```bash
mvn clean install
```

This will:
- Download all dependencies
- Compile the code
- Run all tests
- Create the executable JAR

## Step 4: Run the Application

```bash
mvn spring-boot:run
```

The application will start on port 8080. You should see:
```
Started CardEngineApplication in X.XXX seconds
```

## Step 5: Verify API is Running

Open your browser to: http://localhost:8080/swagger-ui.html

You should see the Swagger UI with all available API endpoints.

## Step 6: Run Your First Transaction

### Using curl

```bash
# 1. Create an account with $1000
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "ownerId": "user-001",
    "accountType": "INTERNAL_LEDGER",
    "currency": "USD",
    "initialBalance": 1000.00
  }'
```

Save the `accountId` from the response.

```bash
# 2. Issue a card (replace ACCOUNT_ID with the ID from step 1)
curl -X POST http://localhost:8080/api/v1/cards \
  -H "Content-Type: application/json" \
  -d '{
    "cardholderName": "John Doe",
    "last4": "1234",
    "expirationDate": "2027-12-31",
    "fundingAccountId": "ACCOUNT_ID",
    "ownerId": "user-001"
  }'
```

Save the `cardId` from the response.

```bash
# 3. Authorize a $50 purchase (replace CARD_ID)
curl -X POST http://localhost:8080/api/v1/authorizations \
  -H "Content-Type: application/json" \
  -d '{
    "cardId": "CARD_ID",
    "amount": 50.00,
    "currency": "USD",
    "merchantName": "Coffee Shop",
    "merchantCategoryCode": "5814"
  }'
```

You should receive:
```json
{
  "authorizationId": "...",
  "status": "APPROVED",
  "declineReason": null
}
```

Save the `authorizationId`.

```bash
# 4. Clear the transaction (replace AUTH_ID)
curl -X POST "http://localhost:8080/api/v1/settlement/clear/AUTH_ID?amount=50.00&currency=USD"
```

```bash
# 5. Check your account balance (replace ACCOUNT_ID)
curl http://localhost:8080/api/v1/accounts/ACCOUNT_ID
```

You should see balance of $950.00 (1000 - 50).

```bash
# 6. View ledger history (replace ACCOUNT_ID)
curl http://localhost:8080/api/v1/accounts/ACCOUNT_ID/ledger
```

You should see:
- DEPOSIT (initial $1000)
- AUTH_HOLD (reserve $50)
- CLEARING_COMMIT (commit $50)

### Using HTTP Files

If you're using IntelliJ IDEA or VS Code with REST Client extension:

1. Open `examples/sample-flows/complete-transaction-flow.http`
2. Execute requests one by one
3. Variables are automatically captured between requests

## Step 7: Explore Different Scenarios

Try the example flows in `examples/sample-flows/`:

1. **account-types.http** - Create different account types
2. **declined-transactions.http** - See various decline scenarios
3. **reversal-flow.http** - Process a refund

## Step 8: Run Tests

```bash
mvn test
```

This runs the complete test suite including:
- Authorization flow tests
- Settlement tests
- Account abstraction tests

## Common Commands

### Stop PostgreSQL
```bash
docker-compose down
```

### View PostgreSQL Logs
```bash
docker-compose logs -f postgres
```

### Access PostgreSQL Shell
```bash
docker exec -it card-engine-db psql -U cardengine -d cardengine
```

### Build without Tests
```bash
mvn clean install -DskipTests
```

## Troubleshooting

### Port 8080 Already in Use
Change the port in `src/main/resources/application.yml`:
```yaml
server:
  port: 8081
```

### Cannot Connect to PostgreSQL
1. Check Docker is running: `docker ps`
2. Check PostgreSQL logs: `docker-compose logs postgres`
3. Verify connection settings in `application.yml`

### Tests Failing
Tests use an in-memory H2 database, not PostgreSQL. If tests fail:
1. Check Java version: `java -version` (must be 21+)
2. Clean build: `mvn clean test`

## Next Steps

1. Read [ARCHITECTURE.md](ARCHITECTURE.md) to understand the system design
2. Try modifying rules in `src/main/java/com/cardengine/rules/`
3. Create your own account type implementation
4. Explore the REST API via Swagger UI
5. Implement a custom authorization rule

## API Reference

Full API documentation is available at:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api-docs

## Support

If you encounter issues:
1. Check the logs in the console
2. Review [ARCHITECTURE.md](ARCHITECTURE.md) for system design
3. Open an issue on GitHub
