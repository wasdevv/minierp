package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.support.IntegrationTest;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Sales are inserted with fixed instants, so the zone boundaries of the period can be checked. */
class ReportControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long productId;
    private long mariaId;

    @BeforeEach
    void seed() {
        productId = jdbc.queryForObject("""
                insert into products (name, sku, price, stock_quantity, created_at, updated_at)
                values ('Caneta', 'CN-01', 2.50, 100, now(), now()) returning id""", Long.class);
        mariaId = jdbc.queryForObject("""
                insert into customers (name, document, created_at, updated_at)
                values ('Maria Souza', '52998224725', now(), now()) returning id""", Long.class);
    }

    @Test
    void periodFollowsTheShopsDaysNotUtc() throws Exception {
        sale(null, "2026-10-01T02:59:00Z", 1);  // 30/09 23:59 in São Paulo: outside October
        long first = sale(mariaId, "2026-10-01T03:00:00Z", 4);  // 01/10 00:00: inside
        long last = sale(null, "2026-11-01T02:59:00Z", 2);  // 31/10 23:59: inside
        sale(null, "2026-11-01T03:00:00Z", 8);  // 01/11 00:00: outside

        mvc.perform(get("/reports/sales").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#" + first)))
                .andExpect(content().string(containsString("#" + last)))
                .andExpect(content().string(containsString("01/10/2026 00:00")))
                .andExpect(content().string(containsString("31/10/2026 23:59")))
                .andExpect(content().string(not(containsString("30/09/2026"))))
                .andExpect(content().string(not(containsString("01/11/2026 00:00"))))
                .andExpect(content().string(containsString("R$ 15,00")));
    }

    @Test
    void pageWithoutParametersShowsTheCurrentMonth() throws Exception {
        mvc.perform(get("/reports/sales"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"from\" value=\"")))
                .andExpect(content().string(containsString("Baixar PDF")))
                .andExpect(content().string(containsString("aria-current=\"page\">Relatórios")));
    }

    @Test
    void invalidPeriodShowsMessageAndNoReport() throws Exception {
        mvc.perform(get("/reports/sales").param("from", "2026-10-28").param("to", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A data inicial deve ser anterior ou igual à data final.")))
                .andExpect(content().string(not(containsString("Baixar PDF"))))
                .andExpect(content().string(not(containsString("Total vendido"))));
    }

    @Test
    void emptyPeriodSaysSo() throws Exception {
        mvc.perform(get("/reports/sales").param("from", "2020-01-01").param("to", "2020-01-31"))
                .andExpect(content().string(containsString("Nenhuma venda no período.")))
                .andExpect(content().string(containsString("R$ 0,00")));
    }

    @Test
    void pdfCarriesSalesCountAndTotal() throws Exception {
        long id = sale(mariaId, "2026-10-10T15:00:00Z", 4);
        sale(null, "2026-10-11T15:00:00Z", 1000);

        byte[] pdf = mvc.perform(get("/reports/sales.pdf").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"vendas-2026-10-01-a-2026-10-31.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        String text = text(pdf);
        assertThat(text).contains("Relatório de vendas", "Período: 01/10/2026 a 31/10/2026",
                "#" + id, "10/10/2026 12:00", "Maria Souza", "Consumidor final",
                "R$ 10,00", "R$ 2.500,00", "Quantidade de vendas: 2", "R$ 2.510,00", "Página 1");
        assertThat(text).as("no customer documents in the PDF").doesNotContain("52998224725", "529.982.247-25");
    }

    @Test
    void emptyPdfIsStillAValidDocument() throws Exception {
        byte[] pdf = mvc.perform(get("/reports/sales.pdf").param("from", "2020-01-01").param("to", "2020-01-31"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(text(pdf)).contains("Nenhuma venda no período.").doesNotContain("Total vendido");
    }

    @Test
    void manySalesSpanSeveralPages() throws Exception {
        for (int day = 1; day <= 28; day++) {
            for (int i = 0; i < 3; i++) {
                sale(null, "2026-02-%02dT15:00:00Z".formatted(day), 1);
            }
        }

        byte[] pdf = mvc.perform(get("/reports/sales.pdf").param("from", "2026-02-01").param("to", "2026-02-28"))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        assertThat(text(pdf)).contains("Quantidade de vendas: 84", "R$ 210,00");
    }

    @Test
    void invalidPeriodPdfRedirectsBackToTheForm() throws Exception {
        mvc.perform(get("/reports/sales.pdf").param("from", "2026-10-28").param("to", "x"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/reports/sales?from=2026-10-28&to=x"));
        mvc.perform(get("/reports/sales.pdf"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/reports/sales?from=&to="));
    }

    private long sale(Long customerId, String instant, int quantity) {
        BigDecimal total = new BigDecimal("2.50").multiply(BigDecimal.valueOf(quantity));
        long id = jdbc.queryForObject("insert into sales (customer_id, sale_date, total_amount) values (?, ?, ?) returning id",
                Long.class, customerId, OffsetDateTime.parse(instant), total);
        jdbc.update("insert into sale_items (sale_id, product_id, quantity, unit_price, subtotal) values (?, ?, ?, 2.50, ?)",
                id, productId, quantity, total);
        return id;
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
