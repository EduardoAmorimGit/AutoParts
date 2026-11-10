package com.autoparts_maven.autoparts_maven.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    static final String DEV_DEFAULT_ADMIN_PASSWORD = "admin";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Order matters: the first matching rule wins.
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Customer flow: plate lookup and the compatible products of a vehicle.
                        .requestMatchers(HttpMethod.POST, "/api/vehicles/lookup").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/vehicles/*/products").permitAll()
                        // Vehicles carry plates (personal data under LGPD): reading them by id or
                        // listing the vehicles of a product is administrative.
                        .requestMatchers(HttpMethod.GET, "/api/products/*/vehicles").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers("/api/products/**", "/api/vehicles/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager userDetailsService(
            @Value("${ADMIN_USER:admin}") String username,
            @Value("${ADMIN_PASSWORD:" + DEV_DEFAULT_ADMIN_PASSWORD + "}") String password,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        requireRealPasswordInProduction(password, environment);
        UserDetails admin = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
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
