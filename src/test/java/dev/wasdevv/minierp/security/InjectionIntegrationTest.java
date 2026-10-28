package dev.wasdevv.minierp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.wasdevv.minierp.support.IntegrationTest;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Attack payloads through every way user input reaches the database or a page: query strings, forms, and
 * values that are stored and rendered later. Nothing may answer 500, change data it should not, return rows
 * it should not, or render markup it was given.
 */
class InjectionIntegrationTest extends IntegrationTest {

    static final List<String> SQL_PAYLOADS = List.of(
            "' OR '1'='1",
            "' OR 1=1 --",
            "'; DROP TABLE products; --",
            "1; DELETE FROM sales",
            "\" OR \"\"=\"",
            "%' OR '%'='",
            "' UNION SELECT id, name, sku FROM products --",
            "\\",
            "'||(SELECT pg_sleep(0))||'",
            "1' AND (SELECT COUNT(*) FROM customers) > 0 --");

    static final String XSS = "<script>alert('xss')</script><img src=x onerror=alert(1)>";

    @Autowired
    private JdbcTemplate jdbc;

    private long productId;

    @BeforeEach
    void seed() {
        productId = jdbc.queryForObject("""
                insert into products (name, sku, price, stock_quantity, created_at, updated_at)
                values ('Caneta', 'CN-01', 2.50, 100, now(), now()) returning id""", Long.class);
        jdbc.update("insert into customers (name, created_at, updated_at) values ('Maria Souza', now(), now())");
    }

    @Test
    void sqlPayloadsInSearchAndFilterParametersFindNothingAndBreakNothing() throws Exception {
        for (String payload : SQL_PAYLOADS) {
            for (MockHttpServletRequestBuilder request : List.of(
                    get("/products").param("q", payload),
                    get("/products").param("filter", payload),
                    get("/customers").param("q", payload),
                    get("/sales").param("q", payload),
                    get("/sales").param("status", payload).param("payment", payload),
                    get("/sales").param("from", payload).param("to", payload),
                    get("/stock").param("type", payload),
                    get("/reports/sales").param("from", payload).param("to", payload),
                    get("/reports/sales.pdf").param("from", payload).param("to", payload),
                    get("/products").param("sort", payload),
                    get("/products").param("page", payload).param("size", payload))) {
                MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
                assertThat(response.getStatus()).as("%s with %s", request, payload).isLessThan(500);
                assertThat(response.getContentAsString()).as("no SQL error text leaks").doesNotContainIgnoringCase("sqlexception")
                        .doesNotContainIgnoringCase("syntax error").doesNotContain("org.hibernate");
            }
            assertDataIntact();
        }
    }

    @Test
    void searchPayloadsDoNotWidenTheResults() throws Exception {
        for (String payload : SQL_PAYLOADS) {
            assertThat(mvc.perform(get("/products").param("q", payload)).andReturn().getResponse().getContentAsString())
                    .as(payload).doesNotContain("CN-01");
            assertThat(mvc.perform(get("/customers").param("q", payload)).andReturn().getResponse().getContentAsString())
                    .as(payload).doesNotContain("Maria Souza");
        }
    }

    @Test
    void nonNumericIdsInPathsAndFormsAreRejectedWithoutTouchingTheDatabase() throws Exception {
        for (String payload : SQL_PAYLOADS) {
            int status = mvc.perform(get("/products/{id}/edit", payload)).andReturn().getResponse().getStatus();
            assertThat(status).as(payload).isBetween(400, 499);
            mvc.perform(post("/stock/receipts").param("productId", payload).param("quantity", "1"));
            mvc.perform(post("/sales").param("paymentMethod", "PIX").param("customerId", payload)
                    .param("items[0].productId", payload).param("items[0].quantity", payload));
        }
        assertDataIntact();
        assertThat(jdbc.queryForObject("select count(*) from sales", Integer.class)).isZero();
    }

    @Test
    void sqlPayloadsInFormsAreStoredAsPlainText() throws Exception {
        String name = "'; DROP TABLE products; --";
        mvc.perform(post("/products").param("name", name).param("sku", "SQL-1").param("price", "1")
                .param("stockQuantity", "1").param("description", "' OR '1'='1"));
        mvc.perform(post("/customers").param("name", "Robert'); DROP TABLE customers;--").param("email", "a@b.co"));
        mvc.perform(post("/stock/receipts").param("productId", String.valueOf(productId)).param("quantity", "1")
                .param("reason", "1; DELETE FROM stock_movements"));

        assertThat(jdbc.queryForObject("select name from products where sku = 'SQL-1'", String.class)).isEqualTo(name);
        assertThat(jdbc.queryForObject("select count(*) from customers where name like 'Robert%'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where reason = '1; DELETE FROM stock_movements'",
                Integer.class)).isEqualTo(1);
        assertDataIntact();
    }

    @Test
    void storedMarkupIsEscapedOnEveryPageThatShowsIt() throws Exception {
        mvc.perform(post("/products").param("name", XSS).param("sku", "XSS-1").param("price", "1").param("stockQuantity", "5")
                .param("description", XSS));
        mvc.perform(post("/customers").param("name", XSS).param("email", "x@x.co"));
        long product = jdbc.queryForObject("select id from products where sku = 'XSS-1'", Long.class);
        long customer = jdbc.queryForObject("select id from customers where email = 'x@x.co'", Long.class);
        String location = mvc.perform(post("/sales").param("paymentMethod", "PIX").param("customerId", String.valueOf(customer))
                .param("items[0].productId", String.valueOf(product)).param("items[0].quantity", "1"))
                .andReturn().getResponse().getHeader("Location");
        long sale = Long.parseLong(location.substring("/sales/".length()));
        mvc.perform(post("/stock/receipts").param("productId", String.valueOf(product)).param("quantity", "1").param("reason", XSS));

        for (String page : List.of("/", "/products", "/products/" + product + "/edit", "/customers", "/customers/" + customer,
                "/customers/" + customer + "/edit", "/sales", "/sales/" + sale, "/sales/" + sale + "/receipt", "/sales/new",
                "/stock", "/reports/sales")) {
            String html = mvc.perform(get(page)).andReturn().getResponse().getContentAsString();
            assertThat(html).as(page).doesNotContain("<script>alert").doesNotContain("<img src=x");
        }
        assertThat(mvc.perform(get("/products")).andReturn().getResponse().getContentAsString())
                .contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
        String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
        byte[] pdf = mvc.perform(get("/reports/sales.pdf").param("from", today).param("to", today))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(text(pdf)).as("the PDF shows the name as text").contains("<script>alert('xss')</script>");
    }

    @Test
    void reflectedParametersAreEscaped() throws Exception {
        for (String page : List.of("/products", "/customers", "/sales")) {
            assertThat(mvc.perform(get(page).param("q", XSS)).andReturn().getResponse().getContentAsString())
                    .as(page).doesNotContain("<script>alert").contains("&lt;script&gt;");
        }
        assertThat(mvc.perform(get("/reports/sales").param("from", XSS).param("to", "2026-01-01")).andReturn()
                .getResponse().getContentAsString()).doesNotContain("<script>alert");
    }

    @Test
    void fieldsTheFormDoesNotHaveAreIgnored() throws Exception {
        mvc.perform(post("/products").param("name", "Caderno").param("sku", "CD-01").param("price", "10")
                .param("stockQuantity", "5").param("id", "1").param("version", "99").param("createdAt", "2000-01-01T00:00:00Z"));
        Map<String, Object> created = jdbc.queryForMap("select id, version, created_at from products where sku = 'CD-01'");
        assertThat(((Number) created.get("id")).longValue()).isNotEqualTo(productId);
        assertThat(((Number) created.get("version")).longValue()).isZero();
        assertThat(created.get("created_at").toString()).doesNotStartWith("2000");
    }

    private void assertDataIntact() {
        for (String table : List.of("products", "customers", "sales", "sale_items", "stock_movements")) {
            assertThat(jdbc.queryForObject("select to_regclass(?)::text", String.class, table)).as(table).isEqualTo(table);
        }
        assertThat(jdbc.queryForObject("select count(*) from products where id = ?", Integer.class, productId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from customers where name = 'Maria Souza'", Integer.class)).isEqualTo(1);
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
