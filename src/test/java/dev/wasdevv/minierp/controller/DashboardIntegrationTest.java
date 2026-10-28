package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The home page, with sales inserted at fixed local times relative to today. */
class DashboardIntegrationTest extends IntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void emptyShopShowsOnboarding() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bem-vindo ao Mini ERP")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(containsString("R$ 0,00")));
    }

    @Test
    void kpisCountConfirmedSalesOnly() throws Exception {
        long pen = product("Caneta", "CN-01", "2.50", 100, true);
        LocalDate today = LocalDate.now(ZONE);
        sale(pen, today.atTime(9, 0), 4, false);   // R$ 10,00
        sale(pen, today.atTime(10, 0), 2, false);  // R$ 5,00
        sale(pen, today.atTime(11, 0), 40, true);  // R$ 100,00, cancelled

        String html = home();

        assertThat(kpi(html, "Faturamento hoje")).isEqualTo("R$ 15,00");
        assertThat(html).contains("2 venda(s)").doesNotContain("Bem-vindo");
        assertThat(kpi(html, "Ticket médio no mês")).isEqualTo("R$ 7,50");
    }

    @Test
    void yesterdayLateEveningIsNotToday() throws Exception {
        long pen = product("Caneta", "CN-01", "2.50", 100, true);
        sale(pen, LocalDate.now(ZONE).minusDays(1).atTime(23, 59), 4, false);

        assertThat(kpi(home(), "Faturamento hoje")).isEqualTo("R$ 0,00");
    }

    @Test
    void chartHasFourteenDaysEndingToday() throws Exception {
        long pen = product("Caneta", "CN-01", "2.50", 100, true);
        LocalDate today = LocalDate.now(ZONE);
        sale(pen, today.atTime(12, 0), 4, false);
        sale(pen, today.minusDays(13).atTime(12, 0), 2, false);
        sale(pen, today.minusDays(14).atTime(12, 0), 8, false);  // outside the chart

        String html = home();

        assertThat(count(html, "class=\"bars__day\"")).isEqualTo(14);
        assertThat(html).contains("height: 100%", "height: 50%");
        assertThat(html).contains(String.format("%02d/%02d", today.getDayOfMonth(), today.getMonthValue()));
    }

    @Test
    void topProductsAndLowStock() throws Exception {
        long pen = product("Caneta", "CN-01", "2.50", 100, true);
        long notebook = product("Caderno", "CD-01", "14.90", 100, true);
        product("Mochila", "MC-01", "129.00", 0, true);
        product("Régua", "RG-01", "3.40", 7, true);
        product("Agenda", "AG-01", "9.90", 0, false);  // inactive: not a restock alert
        LocalDate today = LocalDate.now(ZONE);
        sale(pen, today.atTime(9, 0), 10, false);
        sale(notebook, today.atTime(9, 30), 3, false);
        sale(notebook, today.atTime(10, 0), 50, true);  // cancelled: not a best seller

        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Repor estoque")))
                .andExpect(content().string(containsString("Sem estoque")))
                .andExpect(content().string(containsString("Régua")))
                .andExpect(content().string(not(containsString("AG-01"))));

        String html = home();
        String top = html.substring(html.indexOf("Mais vendidos no mês"), html.indexOf("Repor estoque"));
        assertThat(top.indexOf("Caneta")).isLessThan(top.indexOf("Caderno"));
        assertThat(top).contains("CN-01 · 10 un.", "CD-01 · 3 un.", "R$ 44,70");
    }

    @Test
    void recentSalesShowCancelledOnesStruck() throws Exception {
        long pen = product("Caneta", "CN-01", "2.50", 100, true);
        long id = sale(pen, LocalDate.now(ZONE).atTime(9, 0), 4, true);

        mvc.perform(get("/"))
                .andExpect(content().string(containsString("#" + id + " · Consumidor final")))
                .andExpect(content().string(containsString("rows__value struck")));
    }

    private String home() throws Exception {
        return mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String kpi(String html, String label) {
        Matcher m = Pattern.compile(Pattern.quote(label) + "</span>\\s*<strong class=\"kpi__value\">([^<]+)<").matcher(html);
        assertThat(m.find()).as("KPI " + label).isTrue();
        return m.group(1);
    }

    private static int count(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    private long product(String name, String sku, String price, int stock, boolean active) {
        return jdbc.queryForObject("""
                insert into products (name, sku, price, stock_quantity, active, created_at, updated_at)
                values (?, ?, ?, ?, ?, now(), now()) returning id""", Long.class, name, sku, new BigDecimal(price), stock, active);
    }

    private long sale(long productId, LocalDateTime localTime, int quantity, boolean cancelled) {
        BigDecimal price = jdbc.queryForObject("select price from products where id = ?", BigDecimal.class, productId);
        BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
        OffsetDateTime when = localTime.atZone(ZONE).toOffsetDateTime();
        long id = jdbc.queryForObject("""
                insert into sales (sale_date, total_amount, status, cancelled_at, cancel_reason)
                values (?, ?, ?, ?, ?) returning id""", Long.class, when, total,
                cancelled ? "CANCELLED" : "CONFIRMED", cancelled ? when : null, cancelled ? "teste" : null);
        jdbc.update("insert into sale_items (sale_id, product_id, quantity, unit_price, subtotal) values (?, ?, ?, ?, ?)",
                id, productId, quantity, price, total);
        return id;
    }
}
