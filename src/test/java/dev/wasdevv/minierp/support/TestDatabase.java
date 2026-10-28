package dev.wasdevv.minierp.support;

import org.springframework.test.context.DynamicPropertyRegistry;

/** The PostgreSQL database integration tests run against, from {@code MINIERP_TEST_*} variables. */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static boolean configured() {
        String url = System.getenv("MINIERP_TEST_JDBC_URL");
        return url != null && !url.isBlank();
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MINIERP_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> env("MINIERP_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("MINIERP_TEST_DB_PASSWORD", ""));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
