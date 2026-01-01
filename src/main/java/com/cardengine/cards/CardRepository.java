package com.cardengine.cards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for card persistence.
 */
@Repository
public interface CardRepository extends JpaRepository<Card, String> {

    Optional<Card> findByCardId(String cardId);

    List<Card> findByOwnerId(String ownerId);

    List<Card> findByFundingAccountId(String fundingAccountId);
}
