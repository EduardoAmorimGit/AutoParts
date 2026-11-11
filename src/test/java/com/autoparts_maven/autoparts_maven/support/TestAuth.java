package com.autoparts_maven.autoparts_maven.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** MockMvc authentication with a JWT principal, bypassing token signing. */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    public static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject("2")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
