package com.autoparts_maven.autoparts_maven.notification;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    @Query("select e.id from EmailOutbox e where e.status = 'PENDING' and e.nextAttemptAt <= :now order by e.id")
    List<Long> findDueIds(Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailOutbox e where e.id = :id")
    Optional<EmailOutbox> findForUpdate(Long id);

    List<EmailOutbox> findByOrderIdOrderByIdAsc(Long orderId);

    Page<EmailOutbox> findByStatus(EmailOutbox.Status status, Pageable pageable);
}
