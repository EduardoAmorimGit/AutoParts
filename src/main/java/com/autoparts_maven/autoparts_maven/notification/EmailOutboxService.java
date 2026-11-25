package com.autoparts_maven.autoparts_maven.notification;

import com.autoparts_maven.autoparts_maven.notification.EmailSender.EmailSendException;
import com.autoparts_maven.autoparts_maven.notification.EmailSender.OutgoingEmail;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox of customer e-mails. {@link #enqueue} joins the caller's transaction, so an e-mail exists
 * exactly when the change it reports was committed. {@link #dispatchDue} sends outside any
 * transaction, retrying with growing pauses up to {@code notifications.email.max-attempts}.
 */
@Service
public class EmailOutboxService {

    // While one dispatcher sends an e-mail, others skip it until this lease expires.
    private static final Duration SEND_LEASE = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);

    private final EmailOutboxRepository repository;
    private final EmailSender sender;
    private final TransactionTemplate transactions;
    private final int maxAttempts;

    public EmailOutboxService(EmailOutboxRepository repository, EmailSender sender, TransactionTemplate transactions,
            @Value("${notifications.email.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.sender = sender;
        this.transactions = transactions;
        this.maxAttempts = maxAttempts;
    }

    static final String REDACTED_BODY = "[conteúdo removido após o envio: continha um link de uso único]";

    public record Email(Long userId, Long orderId, String kind, String to, String subject, String body,
            boolean sensitive) {

        public Email(Long userId, Long orderId, String kind, String to, String subject, String body) {
            this(userId, orderId, kind, to, subject, body, false);
        }
    }

    @Transactional
    public EmailOutbox enqueue(Email email) {
        EmailOutbox entry = new EmailOutbox();
        entry.setUserId(email.userId());
        entry.setOrderId(email.orderId());
        entry.setKind(email.kind());
        entry.setToAddress(email.to());
        entry.setSubject(truncate(email.subject(), 200));
        entry.setBody(email.body());
        entry.setSensitive(email.sensitive());
        entry.setNextAttemptAt(Instant.now());
        return repository.save(entry);
    }

    /** Sends the e-mails due at {@code now}; returns how many were sent. */
    public int dispatchDue(Instant now, int batchSize) {
        List<Long> ids = repository.findDueIds(now, PageRequest.of(0, batchSize));
        int sent = 0;
        for (Long id : ids) {
            OutgoingEmail claimed = transactions.execute(status -> claim(id, now));
            if (claimed == null) {
                continue;
            }
            try {
                sender.send(claimed);
                transactions.executeWithoutResult(status -> markSent(id));
                sent++;
            } catch (EmailSendException exception) {
                transactions.executeWithoutResult(status -> markFailedAttempt(id, exception.getMessage()));
            }
        }
        return sent;
    }

    private OutgoingEmail claim(Long id, Instant now) {
        EmailOutbox entry = repository.findForUpdate(id).orElse(null);
        if (entry == null || entry.getStatus() != EmailOutbox.Status.PENDING || entry.getNextAttemptAt().isAfter(now)) {
            return null;
        }
        entry.setAttempts(entry.getAttempts() + 1);
        entry.setNextAttemptAt(now.plus(SEND_LEASE));
        return new OutgoingEmail(entry.getToAddress(), entry.getSubject(), entry.getBody());
    }

    private void markSent(Long id) {
        repository.findForUpdate(id).ifPresent(entry -> {
            entry.setStatus(EmailOutbox.Status.SENT);
            entry.setSentAt(Instant.now());
            entry.setLastError(null);
            redactIfSensitive(entry);
        });
    }

    private void markFailedAttempt(Long id, String error) {
        repository.findForUpdate(id).ifPresent(entry -> {
            entry.setLastError(truncate(error, 500));
            if (entry.getAttempts() >= maxAttempts) {
                entry.setStatus(EmailOutbox.Status.FAILED);
                redactIfSensitive(entry);
                log.warn("E-mail {} ({}) failed {} times; giving up", id, entry.getKind(), entry.getAttempts());
            } else {
                // 1, 4, 9, 16... minutes between attempts.
                long minutes = (long) entry.getAttempts() * entry.getAttempts();
                entry.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(minutes)));
            }
        });
    }

    private static void redactIfSensitive(EmailOutbox entry) {
        if (entry.isSensitive()) {
            entry.setBody(REDACTED_BODY);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
