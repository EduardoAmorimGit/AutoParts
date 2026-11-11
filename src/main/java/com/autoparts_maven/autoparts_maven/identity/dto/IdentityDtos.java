package com.autoparts_maven.autoparts_maven.identity.dto;

import com.autoparts_maven.autoparts_maven.identity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request and response bodies of the identity endpoints. */
public final class IdentityDtos {

    // BCrypt only uses the first 72 bytes of a password; longer values are rejected, not truncated.
    private static final int MAX_PASSWORD = 72;
    private static final int MIN_PASSWORD = 8;

    private IdentityDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = MIN_PASSWORD, max = MAX_PASSWORD) String password,
            @NotBlank @Size(max = 150) String fullName) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = MAX_PASSWORD) String password) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = MAX_PASSWORD) String currentPassword,
            @NotBlank @Size(min = MIN_PASSWORD, max = MAX_PASSWORD) String newPassword) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            long refreshExpiresIn) {
    }

    public record UserResponse(Long id, String email, String fullName, String role, Instant createdAt) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                    user.getRole().name(), user.getCreatedAt());
        }
    }
}
