package com.autoparts_maven.autoparts_maven.returns;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByOrderIdOrderByIdAsc(Long orderId);

    @Query("select r from ReturnRequest r where (:status is null or r.status = :status)")
    Page<ReturnRequest> search(ReturnStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.id = :id")
    Optional<ReturnRequest> findForUpdate(Long id);

    /** Units of each order item already claimed by requests that were not rejected. */
    @Query("""
            select i.orderItemId, sum(i.quantity) from ReturnRequest r join r.items i
            where r.orderId = :orderId and r.status not in :ignored group by i.orderItemId""")
    List<Object[]> claimedQuantities(Long orderId, Collection<ReturnStatus> ignored);
}
