package org.sid.Product_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import org.sid.Product_service.entity.ProductStatus;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String brand,
        String sku,
        BigDecimal price,
        int stockQuantity,
        ProductStatus status,
        boolean available,
        CategoryResponse category,
        Instant createdAt,
        Instant updatedAt
) {
}
