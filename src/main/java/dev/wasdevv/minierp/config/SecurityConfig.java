package dev.wasdevv.minierp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every page requires login; forms carry Spring Security's CSRF token (Thymeleaf adds it to every
 * {@code th:action} POST form). There is one operator account, configured by environment variables.
 */
@Configuration
public class SecurityConfig {

    static final int MIN_PASSWORD_LENGTH = 8;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico", "/login", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", false).permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Fails the boot without a password instead of falling back to Spring's generated one, which would be
     * printed in the log.
     */
    @Bean
    public UserDetailsService users(@Value("${minierp.admin.username:admin}") String username,
                                    @Value("${minierp.admin.password:}") String password,
                                    PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(requirePassword(password)))
                .roles("OPERATOR")
                .build());
    }

    static String requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Set MINIERP_ADMIN_PASSWORD: every page requires login.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("MINIERP_ADMIN_PASSWORD must have at least " + MIN_PASSWORD_LENGTH
                    + " characters.");
        }
        return password;
    }
}
