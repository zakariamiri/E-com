package org.sid.Auth_service.dto;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean enabled,
        Instant createdAt,
        Set<String> roles
) {
}
