package com.autoparts_maven.autoparts_maven.notification;

/** Delivers one e-mail now. Implementations throw {@link EmailSendException} when it could not be sent. */
public interface EmailSender {

    record OutgoingEmail(String to, String subject, String text) {
    }

    void send(OutgoingEmail email);

    class EmailSendException extends RuntimeException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
