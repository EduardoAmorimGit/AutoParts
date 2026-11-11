package com.autoparts_maven.autoparts_maven.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from {@code ADMIN_EMAIL}/{@code ADMIN_PASSWORD} when no admin
 * exists yet. It never changes an existing account. Under the 'prod' profile a blank or default
 * password stops the application.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    static final String DEV_DEFAULT_ADMIN_PASSWORD = "admin";
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final AuthService authService;
    private final Environment environment;
    private final String email;
    private final String password;

    AdminBootstrap(UserRepository userRepository, AuthService authService, Environment environment,
            @Value("${ADMIN_EMAIL:admin@autoparts.local}") String email,
            @Value("${ADMIN_PASSWORD:" + DEV_DEFAULT_ADMIN_PASSWORD + "}") String password) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.environment = environment;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        requireRealPasswordInProduction(password, environment);
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        String normalizedEmail = AuthService.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("No administrator exists and ADMIN_EMAIL belongs to a non-admin account; none was created");
            return;
        }
        userRepository.save(authService.newUser(normalizedEmail, password, "Administrator", Role.ADMIN));
        log.info("Initial administrator account created");
    }

    // The dev default keeps local setup simple, but must never reach production silently.
    static void requireRealPasswordInProduction(String password, Environment environment) {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        if (production && (password == null || password.isBlank() || DEV_DEFAULT_ADMIN_PASSWORD.equals(password))) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be set to a non-default value when the 'prod' profile is active");
        }
    }
}
