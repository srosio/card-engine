package com.cardengine.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ledger entries.
 */
@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntry, String> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<LedgerEntry> findByTransactionId(String transactionId);

    List<LedgerEntry> findByAuthorizationId(String authorizationId);

    List<LedgerEntry> findByCardId(String cardId);

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);
}
