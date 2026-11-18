package org.sid.Cart_service.dto;

import jakarta.validation.constraints.NotNull;

public record CreateCartRequest(
        @NotNull(message = "userId is required")
        Long userId
) {}

