package org.sid.Payment_service.dto;

import jakarta.validation.constraints.NotNull;
import org.sid.Payment_service.entity.PaymentStatus;

public record UpdatePaymentStatusRequest(@NotNull PaymentStatus status) {
}
