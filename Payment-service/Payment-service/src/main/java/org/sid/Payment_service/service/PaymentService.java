package org.sid.Payment_service.service;

import org.sid.Payment_service.dto.CreatePaymentRequest;
import org.sid.Payment_service.dto.PaymentResponse;
import org.sid.Payment_service.dto.UpdatePaymentStatusRequest;
import org.sid.Payment_service.entity.Payment;
import org.sid.Payment_service.entity.PaymentStatus;
import org.sid.Payment_service.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PaymentService {
    private final PaymentRepository payments;

    public PaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        Payment payment = new Payment();
        payment.setOrderId(request.orderId());
        payment.setAmount(request.amount());
        return toResponse(payments.save(payment));
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
        if (currentStatus != requestedStatus && currentStatus != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le statut d'une tentative de paiement terminée ne peut plus être modifié.");
        }
        payment.setStatus(requestedStatus);
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
