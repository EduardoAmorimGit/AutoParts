package com.autoparts_maven.autoparts_maven.identity;

final class IdentityExceptions {

    private IdentityExceptions() {
    }

    static class EmailAlreadyRegisteredException extends RuntimeException {
        EmailAlreadyRegisteredException() {
            super("This e-mail is already registered");
        }
    }

    /** Same message for unknown e-mail, wrong password or disabled account: no account enumeration. */
    static class InvalidCredentialsException extends RuntimeException {
        InvalidCredentialsException() {
            super("Invalid e-mail or password");
        }
    }

    static class InvalidRefreshTokenException extends RuntimeException {
        InvalidRefreshTokenException() {
            super("Refresh token is invalid or expired");
        }
    }

    static class WrongCurrentPasswordException extends RuntimeException {
        WrongCurrentPasswordException() {
            super("Current password is incorrect");
        }
    }

    static class UserNotFoundException extends RuntimeException {
        UserNotFoundException() {
            super("User not found");
        }
    }

    /** Same message for unknown, used and expired links. */
    static class InvalidPasswordResetTokenException extends RuntimeException {
        InvalidPasswordResetTokenException() {
            super("This password reset link is invalid or has expired; request a new one");
        }
    }
}
