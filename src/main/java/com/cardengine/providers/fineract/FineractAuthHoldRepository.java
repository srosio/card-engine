package com.cardengine.providers.fineract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Fineract auth hold tracking.
 */
@Repository
public interface FineractAuthHoldRepository extends JpaRepository<FineractAuthHold, String> {

    Optional<FineractAuthHold> findByAuthorizationId(String authorizationId);

    List<FineractAuthHold> findByFineractAccountIdAndStatus(
        Long fineractAccountId,
        FineractAuthHold.HoldStatus status
    );
}
