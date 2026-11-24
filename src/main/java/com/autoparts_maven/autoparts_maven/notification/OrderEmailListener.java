package com.autoparts_maven.autoparts_maven.notification;

import com.autoparts_maven.autoparts_maven.identity.AuthService;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.UserResponse;
import com.autoparts_maven.autoparts_maven.notification.EmailOutboxService.Email;
import com.autoparts_maven.autoparts_maven.notification.OrderEmails.Content;
import com.autoparts_maven.autoparts_maven.order.OrderEvent;
import com.autoparts_maven.autoparts_maven.order.OrderService;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderResponse;
import com.autoparts_maven.autoparts_maven.payment.PaymentRefundedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns order and refund events into outbox e-mails. Runs synchronously inside the publishing
 * transaction: the e-mail is stored if and only if the change is committed.
 */
@Component
class OrderEmailListener {

    private final OrderService orderService;
    private final AuthService authService;
    private final OrderEmails emails;
    private final EmailOutboxService outbox;

    OrderEmailListener(OrderService orderService, AuthService authService, OrderEmails emails,
            EmailOutboxService outbox) {
        this.orderService = orderService;
        this.authService = authService;
        this.emails = emails;
        this.outbox = outbox;
    }

    @EventListener
    void on(OrderEvent event) {
        OrderResponse order = orderService.get(event.orderId());
        UserResponse customer = authService.me(order.userId());
        Content content = switch (event.type()) {
            case PLACED -> emails.placed(customer.fullName(), order);
            case PAID -> emails.paid(customer.fullName(), order);
            case SHIPPED -> emails.shipped(customer.fullName(), order);
            case DELIVERED -> emails.delivered(customer.fullName(), order);
            case CANCELLED -> emails.cancelled(customer.fullName(), order);
        };
        outbox.enqueue(new Email(customer.id(), order.id(), "ORDER_" + event.type().name(), customer.email(),
                content.subject(), content.body()));
    }

    @EventListener
    void on(PaymentRefundedEvent event) {
        OrderResponse order = orderService.get(event.orderId());
        UserResponse customer = authService.me(order.userId());
        Content content = emails.refunded(customer.fullName(), order.id(), event.amount(), event.method());
        outbox.enqueue(new Email(customer.id(), order.id(), "PAYMENT_REFUNDED", customer.email(),
                content.subject(), content.body()));
    }
}
