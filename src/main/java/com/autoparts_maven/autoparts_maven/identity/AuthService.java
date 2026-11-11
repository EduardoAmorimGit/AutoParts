package com.autoparts_maven.autoparts_maven.identity;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.EmailAlreadyRegisteredException;
import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.InvalidCredentialsException;
import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.InvalidRefreshTokenException;
import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.UserNotFoundException;
import com.autoparts_maven.autoparts_maven.identity.IdentityExceptions.WrongCurrentPasswordException;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.ChangePasswordRequest;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.LoginRequest;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.RegisterRequest;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.TokenResponse;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.UserResponse;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    // Compared against when the e-mail is unknown, so a miss costs the same BCrypt time as a hit.
    private final String dummyPasswordHash;

    AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            TokenService tokenService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** Self-service sign-up always creates a CUSTOMER; admins are never self-registered. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = newUser(email, request.password(), request.fullName().trim(), Role.CUSTOMER);
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user, Instant.now());
    }

    /**
     * Rotates the refresh token: the presented token is revoked and a new pair is issued.
     * Presenting an already revoked token means it leaked or was replayed, so every session of
     * that user is revoked.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String refreshToken) {
        Instant now = Instant.now();
        RefreshToken stored = refreshTokenRepository.findForUpdate(TokenService.hash(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = stored.getUser();

        if (stored.isRevoked()) {
            log.warn("Revoked refresh token reused; revoking all sessions of user {}", user.getId());
            refreshTokenRepository.revokeAllForUser(user.getId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (stored.isExpiredAt(now) || !user.isEnabled()) {
            throw new InvalidRefreshTokenException();
        }

        stored.setRevokedAt(now);
        return issueTokens(user, now);
    }

    /** Idempotent: an unknown or already revoked token is ignored. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findForUpdate(TokenService.hash(refreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.from(getUser(userId));
    }

    /** Changing the password signs the user out everywhere (all refresh tokens revoked). */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new WrongCurrentPasswordException();
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable).map(UserResponse::from));
    }

    User newUser(String normalizedEmail, String rawPassword, String fullName, Role role) {
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private TokenResponse issueTokens(User user, Instant now) {
        String refreshToken = tokenService.newRefreshToken();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash(TokenService.hash(refreshToken));
        stored.setExpiresAt(tokenService.refreshTokenExpiry(now));
        refreshTokenRepository.save(stored);

        return new TokenResponse(
                tokenService.issueAccessToken(user, now),
                "Bearer",
                tokenService.accessTokenTtlSeconds(),
                refreshToken,
                tokenService.refreshTokenTtlSeconds());
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
