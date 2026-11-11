package com.autoparts_maven.autoparts_maven.order;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query("select o from CustomerOrder o where (:status is null or o.status = :status)")
    Page<Order> search(OrderStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<Order> findForUpdate(Long id);

    @Query("select o.id from CustomerOrder o where o.status = :status and o.paymentDueAt < :now order by o.paymentDueAt")
    List<Long> findIdsDueBefore(OrderStatus status, Instant now, Pageable pageable);
}
