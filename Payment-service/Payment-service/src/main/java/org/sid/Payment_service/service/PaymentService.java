package org.sid.Payment_service.service;

import org.sid.Payment_service.dto.CreatePaymentRequest;
import org.sid.Payment_service.dto.PaymentResponse;
import org.sid.Payment_service.dto.OrderInfo;
import org.sid.Payment_service.dto.UpdatePaymentStatusRequest;
import org.sid.Payment_service.entity.Payment;
import org.sid.Payment_service.entity.PaymentStatus;
import org.sid.Payment_service.repository.PaymentRepository;
import org.sid.Payment_service.event.PaymentStatusChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PaymentService {
    private final PaymentRepository payments;
    private final OrderClient orders;
    private final ApplicationEventPublisher events;

    public PaymentService(PaymentRepository payments, OrderClient orders,
                          ApplicationEventPublisher events) {
        this.payments = payments;
        this.orders = orders;
        this.events = events;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse create(CreatePaymentRequest request, String idempotencyKey) {
        String key = idempotencyKey.strip();
        if (key.isEmpty() || key.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key doit contenir entre 1 et 100 caractères.");
        }

        int inserted = payments.insertIfAbsent(request.orderId(), request.amount(), key);

        Payment payment = payments.findByIdempotencyKey(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Le paiement associé à la clé est introuvable."));

        if (inserted == 0) {
            boolean sameOrder = payment.getOrderId().equals(request.orderId());
            boolean sameAmount = payment.getAmount().compareTo(request.amount()) == 0;
            if (!sameOrder || !sameAmount) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cette clé est déjà associée à une autre demande de paiement.");
            }

            return toResponse(payment);
        }

        OrderInfo order = orders.getOrder(request.orderId());
        if (!"CREATED".equals(order.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La commande doit être au statut CREATED.");
        }

        if (request.amount().compareTo(order.totalAmount()) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Le montant doit correspondre au total de la commande.");
        }

        return toResponse(payment);
    }

    public List<PaymentResponse> findAll() {
        return payments.findAll().stream().map(this::toResponse).toList();
    }

    public PaymentResponse findById(Long id) {
        return toResponse(payments.findById(id).orElseThrow(this::paymentNotFound));
    }

    @Transactional
    public PaymentResponse updateStatus(Long id, UpdatePaymentStatusRequest request) {
        Payment payment = payments.findByIdForUpdate(id).orElseThrow(this::paymentNotFound);
        PaymentStatus currentStatus = payment.getStatus();
        PaymentStatus requestedStatus = request.status();
        if (currentStatus == requestedStatus) {
            return toResponse(payment);
        }
        if (currentStatus != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le statut d'une tentative de paiement terminée ne peut plus être modifié.");
        }
        if (requestedStatus == PaymentStatus.SUCCEEDED) {
            if (payments.existsByOrderIdAndStatus(payment.getOrderId(), PaymentStatus.SUCCEEDED)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cette commande possède déjà un paiement réussi.");
            }
            orders.confirmOrder(payment.getOrderId());
        }
        payment.setStatus(requestedStatus);
        try {
            payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException failure) {
            if (failure.getMostSpecificCause() instanceof java.sql.SQLException cause
                    && "23505".equals(cause.getSQLState())
                    && cause.getMessage().contains("ux_payments_one_succeeded_order")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cette commande possède déjà un paiement réussi.", failure);
            }
            throw failure;
        }
        events.publishEvent(new PaymentStatusChangedEvent(
                UUID.randomUUID(), "PAYMENT_STATUS_CHANGED", payment.getId(),
                payment.getOrderId(), payment.getAmount(), requestedStatus.name(), Instant.now()));
        return toResponse(payment);
    }

    @Transactional
    public void delete(Long id) {
        payments.delete(payments.findByIdForUpdate(id).orElseThrow(this::paymentNotFound));
    }

    private ResponseStatusException paymentNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable.");
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getStatus(), payment.getTransactionReference(), payment.getCreatedAt());
    }
}
