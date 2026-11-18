package org.sid.Cart_service.dto;

import java.time.Instant;
import java.util.List;

public record CartResponse(
        Long id,
        Long userId,
        Instant updatedAt,
        List<CartItemResponse> items,
        int totalItemsCount
) {}

