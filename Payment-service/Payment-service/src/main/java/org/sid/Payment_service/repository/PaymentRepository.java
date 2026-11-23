package org.sid.Payment_service.repository;

import jakarta.persistence.LockModeType;
import org.sid.Payment_service.entity.Payment;
import org.sid.Payment_service.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Modifying;

@RepositoryRestResource(exported = false)
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
    INSERT INTO public.payments
        (order_id, amount, status, created_at, idempotency_key)
    VALUES
        (:orderId, :amount, 'PENDING', CURRENT_TIMESTAMP, :key)
    ON CONFLICT (idempotency_key) DO NOTHING
    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("orderId") Long orderId,
            @Param("amount") BigDecimal amount,
            @Param("key") String key);
}
