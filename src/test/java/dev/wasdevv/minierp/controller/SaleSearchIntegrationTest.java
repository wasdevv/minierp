package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Search and filters of the sales list, over sales inserted at fixed instants. */
class SaleSearchIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long maria;
    private long joao;
    private long a;
    private long b;
    private long c;
    private long d;

    @BeforeEach
    void seed() {
        maria = customer("Maria Souza");
        joao = customer("João Lima");
        a = sale(maria, "2026-10-01T15:00:00Z", "10.00", "PIX", false);
        b = sale(maria, "2026-10-10T15:00:00Z", "20.00", "CASH", false);
        c = sale(joao, "2026-10-10T18:00:00Z", "30.00", "PIX", true);
        d = sale(null, "2026-10-20T15:00:00Z", "40.00", "CREDIT_CARD", false);
    }

    @Test
    void withoutFiltersListsEverything() throws Exception {
        assertThat(list(get("/sales"))).contains(tag(a), tag(b), tag(c), tag(d)).doesNotContain("no filtro");
    }

    @Test
    void searchesByCustomerNameIgnoringCase() throws Exception {
        assertThat(list(get("/sales").param("q", "maria")))
                .contains(tag(a), tag(b)).doesNotContain(tag(c), tag(d))
                .contains("2 venda(s) confirmada(s) no filtro · total R$ 30,00");
    }

    @Test
    void searchesBySaleNumberWithOrWithoutHash() throws Exception {
        assertThat(list(get("/sales").param("q", "#" + c))).contains(tag(c)).doesNotContain(tag(a), tag(b), tag(d));
        assertThat(list(get("/sales").param("q", String.valueOf(d)))).contains(tag(d)).doesNotContain(tag(a), tag(b), tag(c));
    }

    @Test
    void filtersByPeriodInTheShopsDays() throws Exception {
        assertThat(list(get("/sales").param("from", "2026-10-10").param("to", "2026-10-10")))
                .contains(tag(b), tag(c)).doesNotContain(tag(a), tag(d));
        assertThat(list(get("/sales").param("from", "2026-10-15"))).contains(tag(d)).doesNotContain(tag(a), tag(b), tag(c));
        assertThat(list(get("/sales").param("to", "2026-10-01"))).contains(tag(a)).doesNotContain(tag(b), tag(c), tag(d));
    }

    @Test
    void filtersByStatusAndPaymentMethod() throws Exception {
        assertThat(list(get("/sales").param("status", "CANCELLED"))).contains(tag(c)).doesNotContain(tag(a), tag(b), tag(d))
                .contains("0 venda(s) confirmada(s) no filtro · total R$ 0,00");
        assertThat(list(get("/sales").param("payment", "PIX"))).contains(tag(a), tag(c)).doesNotContain(tag(b), tag(d))
                .contains("1 venda(s) confirmada(s) no filtro · total R$ 10,00");
    }

    @Test
    void filtersCombine() throws Exception {
        assertThat(list(get("/sales").param("q", "maria").param("payment", "CASH").param("from", "2026-10-05")))
                .contains(tag(b)).doesNotContain(tag(a), tag(c), tag(d));
    }

    @Test
    void wildcardsInTheSearchAreLiteral() throws Exception {
        assertThat(list(get("/sales").param("q", "%"))).contains("Nenhuma venda encontrada.");
    }

    @Test
    void badValuesAreIgnoredOrExplainedNeverA400() throws Exception {
        assertThat(list(get("/sales").param("status", "nope").param("payment", "BITCOIN")))
                .contains(tag(a), tag(b), tag(c), tag(d));
        assertThat(list(get("/sales").param("from", "2026-10-20").param("to", "2026-10-01")))
                .contains("A data inicial deve ser anterior ou igual à data final.").contains(tag(a), tag(d));
        assertThat(list(get("/sales").param("from", "ontem")))
                .contains("Use datas válidas no formato AAAA-MM-DD.");
    }

    @Test
    void paginationKeepsTheFilters() throws Exception {
        for (int i = 0; i < 11; i++) {
            sale(maria, "2026-11-0" + (1 + i % 9) + "T15:00:00Z", "1.00", "PIX", false);
        }

        assertThat(list(get("/sales").param("q", "maria").param("payment", "PIX")))
                .contains("q=maria", "payment=PIX", "page=1");
    }

    private String list(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String tag(long id) {
        return "href=\"/sales/" + id + "\"";
    }

    private long customer(String name) {
        return jdbc.queryForObject("insert into customers (name, created_at, updated_at) values (?, now(), now()) returning id",
                Long.class, name);
    }

    private long sale(Long customerId, String instant, String total, String method, boolean cancelled) {
        OffsetDateTime when = OffsetDateTime.parse(instant);
        return jdbc.queryForObject("""
                insert into sales (customer_id, sale_date, subtotal_amount, total_amount, payment_method, status,
                                   cancelled_at, cancel_reason)
                values (?, ?, ?, ?, ?, ?, ?, ?) returning id""", Long.class, customerId, when, new BigDecimal(total),
                new BigDecimal(total), method, cancelled ? "CANCELLED" : "CONFIRMED", cancelled ? when : null,
                cancelled ? "teste" : null);
    }
}
