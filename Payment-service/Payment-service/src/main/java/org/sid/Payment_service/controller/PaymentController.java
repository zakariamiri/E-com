package org.sid.Payment_service.controller;

import jakarta.validation.Valid;
import org.sid.Payment_service.dto.CreatePaymentRequest;
import org.sid.Payment_service.dto.PaymentResponse;
import org.sid.Payment_service.dto.UpdatePaymentStatusRequest;
import org.sid.Payment_service.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse payment = payments.create(request);
        return ResponseEntity.created(URI.create("/api/payments/" + payment.id())).body(payment);
    }

    @GetMapping
    public List<PaymentResponse> findAll() {
        return payments.findAll();
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable("id") Long id) {
        return payments.findById(id);
    }

    @PatchMapping("/{id}/status")
    public PaymentResponse updateStatus(@PathVariable("id") Long id,
                                        @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return payments.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        payments.delete(id);
    }
}
