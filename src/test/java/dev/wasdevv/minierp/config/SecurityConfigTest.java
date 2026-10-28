package dev.wasdevv.minierp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    @Test
    void refusesToStartWithoutPassword() {
        assertThatThrownBy(() -> SecurityConfig.requirePassword("")).hasMessageContaining("MINIERP_ADMIN_PASSWORD");
        assertThatThrownBy(() -> SecurityConfig.requirePassword(null)).hasMessageContaining("MINIERP_ADMIN_PASSWORD");
    }

    @Test
    void refusesShortPassword() {
        assertThatThrownBy(() -> SecurityConfig.requirePassword("1234567")).hasMessageContaining("at least 8");
        assertThat(SecurityConfig.requirePassword("12345678")).isEqualTo("12345678");
    }
}
