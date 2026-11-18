package org.sid.Cart_service.dto;

public record CartItemResponse(
        Long id,
        Long productId,
        Integer quantity
) {}

