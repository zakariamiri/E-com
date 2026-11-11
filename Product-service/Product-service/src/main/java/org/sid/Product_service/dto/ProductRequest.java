package org.sid.Product_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.sid.Product_service.entity.ProductStatus;

public record ProductRequest(
        @NotBlank @Size(max = 180) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 120) String brand,
        @NotBlank @Size(max = 80) String sku,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
        @PositiveOrZero int stockQuantity,
        @NotNull ProductStatus status,
        @NotNull Long categoryId
) {
}
