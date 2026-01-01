package com.cardengine.providers.processor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for processor transaction ID mappings.
 */
@Repository
public interface ProcessorTransactionMappingRepository extends JpaRepository<ProcessorTransactionMapping, String> {

    Optional<ProcessorTransactionMapping> findByProcessorTransactionId(String processorTransactionId);

    Optional<ProcessorTransactionMapping> findByInternalAuthorizationId(String internalAuthorizationId);

    Optional<ProcessorTransactionMapping> findByCardTokenAndProcessorName(String cardToken, String processorName);
}
