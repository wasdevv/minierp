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
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Every page requires login; forms carry Spring Security's CSRF token (Thymeleaf adds it to every
 * {@code th:action} POST form). There is one operator account, configured by environment variables.
 */
@Configuration
public class SecurityConfig {

    static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * Scripts and stylesheets come from this origin only (no inline scripts anywhere); fonts from Google Fonts.
     * Inline style attributes stay allowed: the dashboard bars set their height that way.
     */
    public static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "object-src 'none'");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttempts attempts) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico", "/login", "/error",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login")
                        .successHandler((request, response, auth) -> {
                            attempts.succeeded(request.getRemoteAddr(), auth.getName());
                            savedRequestHandler.onAuthenticationSuccess(request, response, auth);
                        })
                        .failureHandler((request, response, exception) -> {
                            attempts.failed(request.getRemoteAddr(), request.getParameter("username"));
                            response.sendRedirect(request.getContextPath() + "/login?error");
                        })
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .addFilterBefore(new LoginThrottleFilter(attempts), UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()")));
        return http.build();
    }

    private final SavedRequestAwareAuthenticationSuccessHandler savedRequestHandler = successHandler();

    private static SavedRequestAwareAuthenticationSuccessHandler successHandler() {
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
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
