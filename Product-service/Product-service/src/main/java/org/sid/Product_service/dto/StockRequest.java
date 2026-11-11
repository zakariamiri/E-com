package org.sid.Product_service.dto;

import jakarta.validation.constraints.Positive;

public record StockRequest(@Positive int quantity) {
}
