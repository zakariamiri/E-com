package org.sid.Order_service;

import org.junit.jupiter.api.Test;
import org.sid.Order_service.dto.CreateOrderRequest;
import org.sid.Order_service.dto.OrderItemRequest;
import org.sid.Order_service.dto.ProductInfo;
import org.sid.Order_service.dto.UserSummaryResponse;
import org.sid.Order_service.entities.Order;
import org.sid.Order_service.enums.OrderStatus;
import org.sid.Order_service.enums.PaymentMethod;
import org.sid.Order_service.repositories.OrderRepository;
import org.sid.Order_service.services.AuthClient;
import org.sid.Order_service.services.CartClient;
import org.sid.Order_service.services.OrderService;
import org.sid.Order_service.services.ProductClient;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderPaymentMethodTests {
    @Test
    void confirmsCashOnDeliveryAndKeepsCardOrdersAwaitingPayment() {
        OrderRepository orders = mock(OrderRepository.class);
        ProductClient products = mock(ProductClient.class);
        AuthClient auth = mock(AuthClient.class);
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        when(products.getProduct(2L)).thenReturn(new ProductInfo(2L, "Casque", new BigDecimal("59.90"), 5, true));
        when(auth.getUserSummary(1L)).thenReturn(new UserSummaryResponse(1L, "test@example.test", true));
        OrderService service = new OrderService(orders, products, mock(CartClient.class), auth,
                mock(ApplicationEventPublisher.class));

        var items = List.of(new OrderItemRequest(2L, 1));
        var delivery = service.createOrder(
                new CreateOrderRequest(1L, "Adresse de test", items, PaymentMethod.CASH_ON_DELIVERY), "delivery");
        var card = service.createOrder(new CreateOrderRequest(1L, "Adresse de test", items, null), "card");

        assertEquals(OrderStatus.CONFIRMED, delivery.status());
        assertEquals(PaymentMethod.CASH_ON_DELIVERY, delivery.paymentMethod());
        assertEquals(new BigDecimal("59.90"), delivery.totalAmount());
        assertEquals(OrderStatus.CREATED, card.status());
        assertEquals(PaymentMethod.CARD, card.paymentMethod());
    }
}
