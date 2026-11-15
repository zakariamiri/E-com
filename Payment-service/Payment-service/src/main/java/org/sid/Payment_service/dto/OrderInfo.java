package org.sid.Payment_service.dto;

import java.math.BigDecimal;

public record OrderInfo(Long id, BigDecimal totalAmount, String status) {
}
