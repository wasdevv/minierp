package dev.wasdevv.minierp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.config.LoginAttempts;
import dev.wasdevv.minierp.config.SecurityConfig;
import dev.wasdevv.minierp.support.IntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Security headers, the login brute-force guard, the health endpoint, and no inline script anywhere. */
class HardeningIntegrationTest extends IntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private LoginAttempts attempts;

    private MockMvc anonymous;

    @BeforeEach
    void setUp() {
        anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        attempts.succeeded("127.0.0.1", "admin");
        attempts.succeeded("127.0.0.1", "outro");
    }

    @Test
    void everyResponseCarriesTheSecurityHeaders() throws Exception {
        for (String path : List.of("/", "/login", "/products")) {
            anonymous.perform(get(path))
                    .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                    .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                    .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "same-origin"))
                    .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
        }
        mvc.perform(get("/products")).andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void cspHasNoUnsafeScriptSources() {
        assertThat(SecurityConfig.CONTENT_SECURITY_POLICY)
                .doesNotContain("script-src 'self' 'unsafe-inline'").doesNotContain("unsafe-eval").doesNotContain("*");
    }

    @Test
    void templatesHaveNoInlineScriptsOrEventHandlers() throws IOException {
        Pattern inline = Pattern.compile("<script>|<script\\s+(?![^>]*src=)|\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            files.filter(f -> f.toString().endsWith(".html")).forEach(file -> {
                try {
                    assertThat(inline.matcher(Files.readString(file)).find()).as(file.toString()).isFalse();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    @Test
    void repeatedWrongPasswordsLockTheLoginForThatAddressAndUser() throws Exception {
        for (int i = 0; i < 5; i++) {
            anonymous.perform(formLogin("/login").user("admin").password("wrong-" + i)).andExpect(redirectedUrl("/login?error"));
        }

        anonymous.perform(formLogin("/login").user("admin").password(PASSWORD))
                .andExpect(redirectedUrl("/login?locked"));
        anonymous.perform(get("/login").param("locked", ""))
                .andExpect(content().string(containsString("Muitas tentativas de login.")));
        // Another username from the same address is counted separately.
        anonymous.perform(formLogin("/login").user("outro").password("x")).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void successfulLoginResetsTheCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            anonymous.perform(formLogin("/login").user("admin").password("wrong-" + i));
        }
        anonymous.perform(formLogin("/login").user("admin").password(PASSWORD)).andExpect(redirectedUrl("/"));
        for (int i = 0; i < 4; i++) {
            anonymous.perform(formLogin("/login").user("admin").password("wrong-" + i)).andExpect(redirectedUrl("/login?error"));
        }
    }

    @Test
    void healthIsPublicAndRevealsNothingElse() throws Exception {
        anonymous.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(content().string(not(containsString("components"))))
                .andExpect(content().string(not(containsString("db"))));
        anonymous.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        anonymous.perform(get("/actuator/env")).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
    }
}
