package com.autoparts_maven.autoparts_maven.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecurityConfigTest {

    @Test
    void productionRejectsDefaultOrBlankAdminPassword() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> SecurityConfig.requireRealPasswordInProduction("admin", prod))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.requireRealPasswordInProduction(" ", prod))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionAcceptsRealPassword() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatCode(() -> SecurityConfig.requireRealPasswordInProduction("s3cure-Pass!", prod))
                .doesNotThrowAnyException();
    }

    @Test
    void developmentKeepsTheConvenienceDefault() {
        assertThatCode(() -> SecurityConfig.requireRealPasswordInProduction("admin", new MockEnvironment()))
                .doesNotThrowAnyException();
    }
}
