package org.sid.Payment_service.service;

import org.junit.jupiter.api.Test;
import org.sid.Payment_service.dto.CreatePaymentRequest;
import org.sid.Payment_service.dto.OrderInfo;
import org.sid.Payment_service.dto.UpdatePaymentStatusRequest;
import org.sid.Payment_service.entity.Payment;
import org.sid.Payment_service.entity.PaymentStatus;
import org.sid.Payment_service.event.PaymentStatusChangedEvent;
import org.sid.Payment_service.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTests {
    private static class Fixture {
        final Payment payment = new Payment();
        final ArrayList<Object> events = new ArrayList<>();
        boolean alreadyPaid;
        boolean uniqueViolation;
        boolean orderOffline;
        int confirmations;
        int saves;
        int insertAttempts;
        int orderReads;
        final PaymentService service;

        Fixture() throws Exception {
            var id = Payment.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(payment, 1L);
            payment.setOrderId(10L);
            payment.setAmount(new BigDecimal("120.00"));
            PaymentRepository repository = (PaymentRepository) Proxy.newProxyInstance(
                    PaymentRepository.class.getClassLoader(),
                    new Class<?>[]{PaymentRepository.class}, (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "insertIfAbsent" -> insertAttempts++ == 0 ? 1 : 0;
                            case "findByIdempotencyKey" -> Optional.of(payment);
                            case "findByIdForUpdate" -> Optional.of(payment);
                            case "existsByOrderIdAndStatus" -> alreadyPaid;
                            case "saveAndFlush" -> {
                                saves++;
                                if (uniqueViolation) {
                                    throw new DataIntegrityViolationException("duplicate",
                                            new SQLException("ux_payments_one_succeeded_order", "23505"));
                                }
                                yield payment;
                            }
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    });
            OrderFeignClient orderApi = new OrderFeignClient() {
                @Override
                public OrderInfo getOrder(Long orderId) {
                    orderReads++;
                    return new OrderInfo(orderId, payment.getAmount(), "CREATED");
                }

                @Override
                public void confirmOrder(Long orderId) {
                    if (orderOffline) {
                        throw new IllegalStateException("Order-service offline");
                    }
                    confirmations++;
                }
            };
            service = new PaymentService(repository, new OrderClient(orderApi), events::add);
        }
    }

    @Test
    void replayOfCreationReturnsTheSamePayment() throws Exception {
        Fixture fixture = new Fixture();
        var request = new CreatePaymentRequest(10L, new BigDecimal("120.0"));

        var first = fixture.service.create(request, "checkout-10");
        var replay = fixture.service.create(request, "checkout-10");

        assertEquals(first.id(), replay.id());
        assertEquals(1, fixture.orderReads);
    }

    @Test
    void reusedCreationKeyWithAnotherAmountReturnsConflict() throws Exception {
        Fixture fixture = new Fixture();
        fixture.service.create(new CreatePaymentRequest(10L, new BigDecimal("120.00")), "checkout-10");

        var failure = assertThrows(ResponseStatusException.class, () ->
                fixture.service.create(new CreatePaymentRequest(10L, new BigDecimal("121.00")), "checkout-10"));

        assertEquals(409, failure.getStatusCode().value());
        assertEquals(1, fixture.orderReads);
    }

    @Test
    void successfulTransitionPublishesOnceAndRepeatsAreIdempotent() throws Exception {
        Fixture fixture = new Fixture();
        var request = new UpdatePaymentStatusRequest(PaymentStatus.SUCCEEDED);
        fixture.service.updateStatus(1L, request);
        fixture.service.updateStatus(1L, request);
        assertEquals(1, fixture.confirmations);
        assertEquals(1, fixture.saves);
        assertEquals(1, fixture.events.size());
        var event = (PaymentStatusChangedEvent) fixture.events.get(0);
        assertEquals(10L, event.orderId());
        assertEquals("SUCCEEDED", event.status());
        assertNotNull(event.eventId());
    }

    @Test
    void failedTransitionPublishesWithoutConfirmingOrder() throws Exception {
        Fixture fixture = new Fixture();
        fixture.service.updateStatus(1L, new UpdatePaymentStatusRequest(PaymentStatus.FAILED));
        assertEquals(0, fixture.confirmations);
        assertEquals("FAILED", ((PaymentStatusChangedEvent) fixture.events.get(0)).status());
    }

    @Test
    void rejectsAnotherSuccessfulPaymentForSameOrder() throws Exception {
        Fixture fixture = new Fixture();
        fixture.alreadyPaid = true;
        var failure = assertThrows(ResponseStatusException.class, () ->
                fixture.service.updateStatus(1L, new UpdatePaymentStatusRequest(PaymentStatus.SUCCEEDED)));
        assertEquals(409, failure.getStatusCode().value());
        assertTrue(fixture.events.isEmpty());
        assertEquals(0, fixture.confirmations);
    }

    @Test
    void mapsConcurrentDatabaseConflictTo409WithoutPublishing() throws Exception {
        Fixture fixture = new Fixture();
        fixture.uniqueViolation = true;
        var failure = assertThrows(ResponseStatusException.class, () ->
                fixture.service.updateStatus(1L, new UpdatePaymentStatusRequest(PaymentStatus.SUCCEEDED)));
        assertEquals(409, failure.getStatusCode().value());
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void orderFailureKeepsPaymentPendingAndPublishesNothing() throws Exception {
        Fixture fixture = new Fixture();
        fixture.orderOffline = true;
        assertThrows(IllegalStateException.class, () -> fixture.service.updateStatus(
                1L, new UpdatePaymentStatusRequest(PaymentStatus.SUCCEEDED)));
        assertEquals(PaymentStatus.PENDING, fixture.payment.getStatus());
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void rejectsChangesToFinishedPayment() throws Exception {
        Fixture fixture = new Fixture();
        fixture.payment.setStatus(PaymentStatus.FAILED);
        assertThrows(ResponseStatusException.class, () -> fixture.service.updateStatus(
                1L, new UpdatePaymentStatusRequest(PaymentStatus.SUCCEEDED)));
        assertTrue(fixture.events.isEmpty());
    }
}
