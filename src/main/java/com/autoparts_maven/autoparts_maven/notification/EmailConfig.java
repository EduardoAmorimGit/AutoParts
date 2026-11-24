package com.autoparts_maven.autoparts_maven.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Brevo SMTP when its login and key are set; otherwise e-mails are only logged (still stored in the outbox). */
@Configuration
class EmailConfig {

    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${notifications.email.from}") String from,
            @Value("${notifications.email.from-name}") String fromName) {
        JavaMailSender smtp = mailSender.getIfAvailable();
        if (smtp == null || username.isBlank() || password.isBlank()) {
            return new LoggingEmailSender();
        }
        return new SmtpEmailSender(smtp, from, fromName);
    }
}
