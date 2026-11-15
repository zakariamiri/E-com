package org.sid.Order_service.dto;

import jakarta.validation.constraints.Min;

public record StockRequest(
        @Min(1) int quantity
) {}