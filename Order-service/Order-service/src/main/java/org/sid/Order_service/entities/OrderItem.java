package org.sid.Order_service.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Data
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private String productName;

    private Integer quantity;
    private BigDecimal unitPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="order_id",nullable = false)
    private Order order;

    protected OrderItem() {

    }
    public OrderItem(BigDecimal unitPrice, Integer quantity, String productName, Long productId) {
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.productName = productName;
        this.productId = productId;
    }
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

}
