package com.autoparts_maven.autoparts_maven.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Sends through an SMTP relay (Brevo in production) as plain UTF-8 text. */
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;

    SmtpEmailSender(JavaMailSender mailSender, String from, String fromName) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public void send(OutgoingEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.text(), false);
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            // The provider's message may echo addresses; only the exception type goes to the outbox.
            throw new EmailSendException("SMTP send failed: " + exception.getClass().getSimpleName(), exception);
        }
    }
}
