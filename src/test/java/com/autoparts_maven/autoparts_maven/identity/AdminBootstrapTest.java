package com.autoparts_maven.autoparts_maven.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AdminBootstrapTest {

    @Test
    void productionRejectsDefaultOrBlankAdminPassword() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> AdminBootstrap.requireRealPasswordInProduction("admin", prod))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AdminBootstrap.requireRealPasswordInProduction(" ", prod))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionAcceptsRealPassword() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatCode(() -> AdminBootstrap.requireRealPasswordInProduction("s3cure-Pass!", prod))
                .doesNotThrowAnyException();
    }

    @Test
    void developmentKeepsTheConvenienceDefault() {
        assertThatCode(() -> AdminBootstrap.requireRealPasswordInProduction("admin", new MockEnvironment()))
                .doesNotThrowAnyException();
    }
}
