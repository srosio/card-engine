package com.cardengine.bank;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for bank account mappings.
 */
@Repository
public interface BankAccountMappingRepository extends JpaRepository<BankAccountMapping, String> {

    Optional<BankAccountMapping> findByCardId(String cardId);

    List<BankAccountMapping> findByBankClientRef(String bankClientRef);

    List<BankAccountMapping> findByBankAccountRef(String bankAccountRef);
}
