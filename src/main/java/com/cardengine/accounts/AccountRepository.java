package com.cardengine.accounts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for account persistence.
 */
@Repository
public interface AccountRepository extends JpaRepository<BaseAccount, String> {

    Optional<BaseAccount> findByAccountId(String accountId);

    List<BaseAccount> findByOwnerId(String ownerId);
}
