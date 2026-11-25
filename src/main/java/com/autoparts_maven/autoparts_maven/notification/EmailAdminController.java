package com.autoparts_maven.autoparts_maven.notification;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only view of customer e-mails: what was sent to whom and when, and a retry for failures. */
@RestController
@RequestMapping("/api/admin/emails")
class EmailAdminController {

    record EmailView(Long id, Long orderId, String kind, String to, String subject, String body, String status,
            int attempts, String lastError, Instant createdAt, Instant sentAt) {

        static EmailView from(EmailOutbox e) {
            return new EmailView(e.getId(), e.getOrderId(), e.getKind(), e.getToAddress(), e.getSubject(),
                    e.isSensitive() ? EmailOutboxService.REDACTED_BODY : e.getBody(), e.getStatus().name(), e.getAttempts(), e.getLastError(), e.getCreatedAt(),
                    e.getSentAt());
        }
    }

    private final EmailOutboxRepository repository;

    EmailAdminController(EmailOutboxRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<EmailView> list(@RequestParam(defaultValue = "FAILED") EmailOutbox.Status status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(repository.findByStatus(status, pageable).map(EmailView::from));
    }

    @GetMapping("/orders/{orderId}")
    @Transactional(readOnly = true)
    public java.util.List<EmailView> forOrder(@PathVariable Long orderId) {
        return repository.findByOrderIdOrderByIdAsc(orderId).stream().map(EmailView::from).toList();
    }

    /** Puts a failed e-mail back in the queue (e.g. after fixing the Brevo credentials). */
    @PostMapping("/{id}/retry")
    @Transactional
    public EmailView retry(@PathVariable Long id) {
        EmailOutbox entry = repository.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "E-mail not found"));
        if (entry.getStatus() != EmailOutbox.Status.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed e-mails can be retried");
        }
        if (entry.isSensitive()) {
            // The secret link was redacted; the customer must request a new one.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This e-mail held a one-time link; it cannot be resent");
        }
        entry.setStatus(EmailOutbox.Status.PENDING);
        entry.setAttempts(0);
        entry.setNextAttemptAt(Instant.now());
        return EmailView.from(entry);
    }
}
