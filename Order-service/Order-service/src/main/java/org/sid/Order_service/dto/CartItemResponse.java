package org.sid.Order_service.dto;

public record CartItemResponse(
        Long id,
        Long productId,
        Integer quantity
) {
}
