package com.autoparts_maven.autoparts_maven.identity;

import com.autoparts_maven.autoparts_maven.common.ratelimit.FixedWindowRateLimiter;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.ForgotPasswordRequest;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.LoginRequest;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.RefreshRequest;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.RegisterRequest;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.ResetPasswordRequest;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.TokenResponse;
import com.autoparts_maven.autoparts_maven.identity.dto.IdentityDtos.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final FixedWindowRateLimiter authRateLimiter;

    public AuthController(AuthService authService, PasswordResetService passwordResetService,
            @Qualifier("authRateLimiter") FixedWindowRateLimiter authRateLimiter) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        authRateLimiter.acquire(httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        authRateLimiter.acquire(httpRequest.getRemoteAddr());
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Always 202, registered or not, so the endpoint cannot be used to discover accounts. */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        authRateLimiter.acquire(httpRequest.getRemoteAddr());
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        authRateLimiter.acquire(httpRequest.getRemoteAddr());
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
