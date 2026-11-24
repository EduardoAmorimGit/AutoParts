package com.autoparts_maven.autoparts_maven.notification;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Sends pending outbox e-mails. Disabled with {@code notifications.email.dispatch-job.enabled=false} (tests). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "notifications.email.dispatch-job.enabled", havingValue = "true", matchIfMissing = true)
class EmailDispatchJob {

    private static final int BATCH_SIZE = 50;

    private final EmailOutboxService outbox;

    EmailDispatchJob(EmailOutboxService outbox) {
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "${notifications.email.dispatch-interval:PT15S}", initialDelayString = "PT30S")
    void dispatch() {
        outbox.dispatchDue(Instant.now(), BATCH_SIZE);
    }
}
