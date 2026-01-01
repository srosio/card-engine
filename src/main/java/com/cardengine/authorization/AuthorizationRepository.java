package com.cardengine.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for authorization persistence.
 */
@Repository
public interface AuthorizationRepository extends JpaRepository<Authorization, String> {

    Optional<Authorization> findByAuthorizationId(String authorizationId);

    Optional<Authorization> findByIdempotencyKey(String idempotencyKey);

    List<Authorization> findByCardId(String cardId);

    List<Authorization> findByCardIdAndCreatedAtAfter(String cardId, Instant after);

    List<Authorization> findByAccountId(String accountId);
}
