package org.sid.Order_service.repositories;

import org.sid.Order_service.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order,Long> {
    java.util.Optional<Order> findByIdempotencyKey(String idempotencyKey);
    @Override
    @EntityGraph(attributePaths = "items")
    java.util.Optional<Order> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    @Query("select o from Order o where o.id = :id")
    java.util.Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "items")
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
