package com.autoparts_maven.autoparts_maven.order;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Periodically cancels orders not paid within {@code orders.payment-timeout} and returns their
 * stock. Disabled with {@code orders.expiry-job.enabled=false} (tests call the service directly).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "orders.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
class OrderExpiryJob {

    private static final int BATCH_SIZE = 100;

    private final OrderService orderService;

    OrderExpiryJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${orders.expiry-job.interval:PT1M}", initialDelayString = "PT1M")
    void expireUnpaidOrders() {
        orderService.expireOverdue(Instant.now(), BATCH_SIZE);
    }
}
