package com.autoparts_maven.autoparts_maven.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByReturnRequestId(Long returnRequestId);

    List<Refund> findByOrderIdOrderByIdAsc(Long orderId);

    /** Money already given back (or possibly given back: a pending refund may have reached the provider). */
    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.paymentId = :paymentId and r.status <> 'FAILED'")
    BigDecimal sumNotFailed(Long paymentId);

    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.paymentId = :paymentId and r.status = 'SUCCEEDED'")
    BigDecimal sumSucceeded(Long paymentId);
}
