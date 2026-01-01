# Contributing to Card Engine

Thank you for your interest in contributing to Card Engine! This document provides guidelines for contributions.

## Code of Conduct

- Be respectful and inclusive
- Focus on constructive feedback
- Help maintain a welcoming environment for all contributors

## How to Contribute

### Reporting Issues

If you find a bug or have a feature request:
1. Check if the issue already exists
2. If not, create a new issue with:
   - Clear title and description
   - Steps to reproduce (for bugs)
   - Expected vs actual behavior
   - Your environment (Java version, OS, etc.)

### Proposing Changes

1. **Fork** the repository
2. **Create a branch** for your feature: `git checkout -b feature/my-feature`
3. **Make your changes**
4. **Test** your changes thoroughly
5. **Commit** with clear messages
6. **Push** to your fork
7. **Open a Pull Request**

## Development Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/card-engine.git
cd card-engine

# Start PostgreSQL
docker-compose up -d

# Run tests
mvn test

# Run the application
mvn spring-boot:run
```

## Code Standards

### Java Style
- Use Java 21 features where appropriate
- Follow standard Java naming conventions
- Keep methods focused and small
- Add JavaDoc for public APIs

### Testing
- Write tests for new features
- Maintain test coverage
- Use meaningful test names
- Test edge cases and error conditions

### Commit Messages
```
type(scope): brief description

Longer explanation if needed

Fixes #issue-number
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding tests
- `refactor`: Code refactoring
- `chore`: Maintenance tasks

### Example
```
feat(accounts): add support for external bank accounts

Implements new ExternalBankAccount type that integrates
with external banking APIs for real-time balance checks.

Includes:
- New account type implementation
- Integration tests
- API documentation

Fixes #123
```

## Areas for Contribution

### High Priority
- Real card processor integrations (Marqeta, Lithic, Stripe Issuing)
- Advanced fraud detection rules
- Performance optimizations
- Additional account type implementations
- Multi-currency support

### Medium Priority
- Webhook event system
- Enhanced reporting APIs
- Admin dashboard UI
- More comprehensive test coverage
- Documentation improvements

### Good First Issues
Look for issues tagged with `good-first-issue` for beginner-friendly contributions.

## Architecture Guidelines

When adding features:

1. **Maintain Separation of Concerns**
   - Domain logic in domain layer
   - API concerns in API layer
   - Infrastructure in providers

2. **Preserve Account Abstraction**
   - New account types must implement `Account` interface
   - Don't couple card logic to specific account implementations

3. **Immutable Ledger**
   - Never modify existing ledger entries
   - All corrections are new entries

4. **Idempotency**
   - All state-changing operations must be idempotent
   - Use idempotency keys consistently

## Testing Requirements

All contributions must include:

1. **Unit Tests** for business logic
2. **Integration Tests** for flows
3. **Test Coverage** of at least 80% for new code

Run tests:
```bash
mvn test
```

## Documentation

Update documentation when:
- Adding new features
- Changing APIs
- Modifying architecture
- Adding configuration options

Documentation locations:
- `README.md` - Overview and quick start
- `docs/ARCHITECTURE.md` - System design
- `docs/GETTING_STARTED.md` - Setup guide
- JavaDoc - Code-level documentation

## Pull Request Process

1. **Update tests** to cover your changes
2. **Update documentation** if needed
3. **Run full test suite**: `mvn clean test`
4. **Ensure build succeeds**: `mvn clean install`
5. **Write clear PR description**:
   - What changed
   - Why it changed
   - How to test it
6. **Link related issues**

### PR Review Checklist

Your PR should:
- [ ] Have a clear, descriptive title
- [ ] Include tests
- [ ] Update relevant documentation
- [ ] Follow code style guidelines
- [ ] Pass all CI checks
- [ ] Have no merge conflicts

## Questions?

- Open a discussion on GitHub
- Ask in PR comments
- Check existing documentation

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
