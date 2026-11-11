package com.autoparts_maven.autoparts_maven.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtConfigTest {

    @Test
    void productionRequiresASecret() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> JwtConfig.resolveSecret("", prod)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsSecretsShorterThan32Bytes() {
        assertThatThrownBy(() -> JwtConfig.resolveSecret("too-short", new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void developmentFallsBackToARandomKey() {
        assertThat(JwtConfig.resolveSecret("", new MockEnvironment())).hasSize(32);
    }

    @Test
    void acceptsALongEnoughSecret() {
        assertThat(JwtConfig.resolveSecret("0123456789abcdef0123456789abcdef", new MockEnvironment())).hasSize(32);
    }
}
