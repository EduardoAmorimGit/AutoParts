package com.autoparts_maven.autoparts_maven.payment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.provider = :provider and p.providerPaymentId = :providerPaymentId")
    Optional<Payment> findByProviderPaymentIdForUpdate(Payment.Provider provider, String providerPaymentId);

    Page<Payment> findByNeedsAttentionTrue(Pageable pageable);
}
