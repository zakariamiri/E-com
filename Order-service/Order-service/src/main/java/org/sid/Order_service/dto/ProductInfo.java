package org.sid.Order_service.dto;

import java.math.BigDecimal;

public record ProductInfo(
        Long id,
        String name,
        BigDecimal price,
        int stockQuantity,
        boolean available
) {}