package dev.wasdevv.minierp.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spring context against the real test database, emptied before each test. Requests run as a logged-in
 * operator with a CSRF token; {@code SecurityIntegrationTest} covers what happens without them.
 */
@ExtendWith(RequiresDatabase.class)
@SpringBootTest(properties = "minierp.admin.password=" + IntegrationTest.PASSWORD)
@AutoConfigureMockMvc
@Import(IntegrationTest.LoggedInOperator.class)
public abstract class IntegrationTest {

    public static final String PASSWORD = "test-password";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE sale_items, sales, products, customers RESTART IDENTITY");
    }

    @TestConfiguration
    static class LoggedInOperator {

        @Bean
        MockMvcBuilderCustomizer loggedInWithCsrf() {
            return builder -> builder.defaultRequest(get("/").with(user("admin")).with(csrf()));
        }
    }
}
