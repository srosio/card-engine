package com.cardengine.authorization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response to an authorization request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationResponse {

    private String authorizationId;
    private AuthorizationStatus status;
    private String declineReason;

    public static AuthorizationResponse approved(String authorizationId) {
        return AuthorizationResponse.builder()
            .authorizationId(authorizationId)
            .status(AuthorizationStatus.APPROVED)
            .build();
    }

    public static AuthorizationResponse declined(String authorizationId, String reason) {
        return AuthorizationResponse.builder()
            .authorizationId(authorizationId)
            .status(AuthorizationStatus.DECLINED)
            .declineReason(reason)
            .build();
    }
}
