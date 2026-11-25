package com.autoparts_maven.autoparts_maven.notification;

import com.autoparts_maven.autoparts_maven.identity.AuthService;
import com.autoparts_maven.autoparts_maven.identity.PasswordEvents.PasswordChanged;
import com.autoparts_maven.autoparts_maven.identity.PasswordEvents.PasswordResetRequested;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.UserResponse;
import com.autoparts_maven.autoparts_maven.notification.EmailOutboxService.Email;
import com.autoparts_maven.autoparts_maven.notification.OrderEmails.Content;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Account security e-mails, enqueued inside the transaction that changed the account. */
@Component
class AccountEmailListener {

    private final AuthService authService;
    private final OrderEmails emails;
    private final EmailOutboxService outbox;

    AccountEmailListener(AuthService authService, OrderEmails emails, EmailOutboxService outbox) {
        this.authService = authService;
        this.emails = emails;
        this.outbox = outbox;
    }

    @EventListener
    void on(PasswordResetRequested event) {
        UserResponse user = authService.me(event.userId());
        Content content = emails.passwordReset(user.fullName(), event.token(), event.expiresAt());
        // Sensitive: the body holds the link, so it is redacted after sending and hidden from admins.
        outbox.enqueue(new Email(user.id(), null, "PASSWORD_RESET", user.email(), content.subject(),
                content.body(), true));
    }

    @EventListener
    void on(PasswordChanged event) {
        UserResponse user = authService.me(event.userId());
        Content content = emails.passwordChanged(user.fullName(), event.changedAt());
        outbox.enqueue(new Email(user.id(), null, "PASSWORD_CHANGED", user.email(), content.subject(),
                content.body()));
    }
}
