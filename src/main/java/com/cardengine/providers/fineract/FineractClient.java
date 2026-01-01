package com.cardengine.providers.fineract;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP client for Apache Fineract API.
 *
 * Handles:
 * - Authentication (Basic Auth)
 * - HTTP requests/responses
 * - Error handling
 * - Retry logic (if configured)
 */
@Component
@Slf4j
public class FineractClient {

    private final RestTemplate restTemplate;
    private final String fineractBaseUrl;
    private final String tenantId;
    private final String username;
    private final String password;

    public FineractClient(
            @Value("${card-engine.fineract.base-url:http://localhost:8443/fineract-provider/api/v1}") String baseUrl,
            @Value("${card-engine.fineract.tenant:default}") String tenantId,
            @Value("${card-engine.fineract.username:mifos}") String username,
            @Value("${card-engine.fineract.password:password}") String password) {

        this.restTemplate = new RestTemplate();
        this.fineractBaseUrl = baseUrl;
        this.tenantId = tenantId;
        this.username = username;
        this.password = password;

        log.info("Fineract client initialized: baseUrl={}, tenant={}", baseUrl, tenantId);
    }

    /**
     * Get account balance from Fineract.
     */
    public FineractDTOs.AccountBalance getAccountBalance(Long savingsAccountId) {
        String url = fineractBaseUrl + "/savingsaccounts/" + savingsAccountId;

        log.debug("Fetching account balance: accountId={}", savingsAccountId);

        try {
            ResponseEntity<FineractDTOs.AccountBalance> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                FineractDTOs.AccountBalance.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("Error fetching account balance from Fineract: accountId={}", savingsAccountId, e);
            throw new RuntimeException("Failed to fetch account balance", e);
        }
    }

    /**
     * Create a journal entry in Fineract.
     * Used for auth holds and reversals.
     */
    public FineractDTOs.JournalEntryResponse createJournalEntry(FineractDTOs.JournalEntryRequest request) {
        String url = fineractBaseUrl + "/journalentries";

        log.debug("Creating journal entry: reference={}", request.getReferenceNumber());

        try {
            ResponseEntity<FineractDTOs.JournalEntryResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request, createHeaders()),
                FineractDTOs.JournalEntryResponse.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("Error creating journal entry in Fineract: reference={}",
                request.getReferenceNumber(), e);
            throw new RuntimeException("Failed to create journal entry", e);
        }
    }

    /**
     * Make a debit from savings account.
     */
    public FineractDTOs.SavingsTransactionResponse debitAccount(
            Long savingsAccountId,
            FineractDTOs.SavingsTransactionRequest request) {

        String url = fineractBaseUrl + "/savingsaccounts/" + savingsAccountId + "/transactions?command=withdrawal";

        log.debug("Debiting account: accountId={}, amount={}", savingsAccountId, request.getTransactionAmount());

        try {
            ResponseEntity<FineractDTOs.SavingsTransactionResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request, createHeaders()),
                FineractDTOs.SavingsTransactionResponse.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("Error debiting account in Fineract: accountId={}", savingsAccountId, e);
            throw new RuntimeException("Failed to debit account", e);
        }
    }

    /**
     * Make a credit to savings account.
     * Used for reversals (refunds).
     */
    public FineractDTOs.SavingsTransactionResponse creditAccount(
            Long savingsAccountId,
            FineractDTOs.SavingsTransactionRequest request) {

        String url = fineractBaseUrl + "/savingsaccounts/" + savingsAccountId + "/transactions?command=deposit";

        log.debug("Crediting account: accountId={}, amount={}", savingsAccountId, request.getTransactionAmount());

        try {
            ResponseEntity<FineractDTOs.SavingsTransactionResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request, createHeaders()),
                FineractDTOs.SavingsTransactionResponse.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("Error crediting account in Fineract: accountId={}", savingsAccountId, e);
            throw new RuntimeException("Failed to credit account", e);
        }
    }

    /**
     * Create headers with Basic Auth and tenant ID.
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Basic Auth
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);

        // Fineract tenant header
        headers.set("Fineract-Platform-TenantId", tenantId);

        return headers;
    }
}
