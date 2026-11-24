package com.autoparts_maven.autoparts_maven.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development stand-in when no SMTP credentials are set: records that an e-mail would be sent. */
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(OutgoingEmail email) {
        // Only the subject: the recipient is personal data (LGPD) and the body may carry addresses.
        log.info("E-mail not sent (SMTP not configured): {}", email.subject());
    }
}
