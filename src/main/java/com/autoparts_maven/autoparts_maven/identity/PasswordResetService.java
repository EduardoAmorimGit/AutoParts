package com.autoparts_maven.autoparts_maven.identity;

import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.InvalidPasswordResetTokenException;
import com.autoparts_maven.autoparts_maven.identity.PasswordEvents.PasswordChanged;
import com.autoparts_maven.autoparts_maven.identity.PasswordEvents.PasswordResetRequested;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot my password": a single-use link sent by e-mail. The request never reveals whether the
 * e-mail is registered; a new link invalidates older ones, and using one signs the user out
 * everywhere.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Duration ttl;
    private final Duration cooldown;

    PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository resetTokenRepository,
            RefreshTokenRepository refreshTokenRepository, TokenService tokenService,
            PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
            @Value("${auth.password-reset.ttl:30m}") Duration ttl,
            @Value("${auth.password-reset.cooldown:1m}") Duration cooldown) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.ttl = ttl;
        this.cooldown = cooldown;
    }

    /**
     * Sends a reset link when the e-mail belongs to an enabled account. Silent otherwise, and also
     * silent when a link was requested less than {@code cooldown} ago (no mailbox flooding).
     */
    @Transactional
    public void requestReset(String email) {
        Instant now = Instant.now();
        User user = userRepository.findByEmail(AuthService.normalizeEmail(email)).orElse(null);
        if (user == null || !user.isEnabled()
                || resetTokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), now.minus(cooldown))) {
            return;
        }
        resetTokenRepository.invalidateOpenForUser(user.getId(), now);

        String token = tokenService.newRefreshToken();
        PasswordResetToken stored = new PasswordResetToken();
        stored.setUser(user);
        stored.setTokenHash(TokenService.hash(token));
        stored.setExpiresAt(now.plus(ttl));
        resetTokenRepository.save(stored);

        events.publishEvent(new PasswordResetRequested(user.getId(), token, stored.getExpiresAt()));
    }

    /** Sets the new password; unknown, used, expired or disabled-account links all fail the same way. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken stored = resetTokenRepository.findForUpdate(TokenService.hash(token))
                .filter(t -> t.isUsableAt(now) && t.getUser().isEnabled())
                .orElseThrow(InvalidPasswordResetTokenException::new);
        User user = stored.getUser();
        stored.setUsedAt(now);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        passwordChanged(user.getId(), now);
    }

    /** Shared by reset and change: closes open links and sessions and tells the customer. */
    void passwordChanged(Long userId, Instant now) {
        resetTokenRepository.invalidateOpenForUser(userId, now);
        refreshTokenRepository.revokeAllForUser(userId, now);
        events.publishEvent(new PasswordChanged(userId, now));
    }
}
