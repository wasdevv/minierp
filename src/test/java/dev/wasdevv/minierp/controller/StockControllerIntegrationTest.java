package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Goods in, inventory counts, and the ledger every stock change writes to. */
class StockControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void newProductOpensItsLedgerWithTheInitialStock() throws Exception {
        long id = createProduct("CN-01", 12);

        assertThat(movements(id)).singleElement().satisfies(m -> {
            assertThat(m).containsEntry("type", "INITIAL").containsEntry("quantity", 12).containsEntry("balance_after", 12)
                    .containsEntry("created_by", "admin");
        });
    }

    @Test
    void productCreatedWithZeroHasNoLedgerLine() throws Exception {
        assertThat(movements(createProduct("CN-01", 0))).isEmpty();
    }

    @Test
    void receiptAddsUnitsAndIsRecorded() throws Exception {
        long id = createProduct("CN-01", 5);

        mvc.perform(post("/stock/receipts").param("productId", String.valueOf(id)).param("quantity", "24")
                        .param("reason", "  NF 1234  "))
                .andExpect(redirectedUrl("/stock?productId=" + id))
                .andExpect(flash().attribute("notice", "Entrada de 24 un. de Caneta registrada. Estoque agora: 29."));

        assertThat(stock(id)).isEqualTo(29);
        assertThat(movements(id).getLast()).containsEntry("type", "RECEIPT").containsEntry("quantity", 24)
                .containsEntry("balance_after", 29).containsEntry("reason", "NF 1234");
    }

    @Test
    void receiptNeedsAPositiveQuantity() throws Exception {
        long id = createProduct("CN-01", 5);

        mvc.perform(post("/stock/receipts").param("productId", String.valueOf(id)).param("quantity", "0"))
                .andExpect(flash().attribute("alert", "Informe uma quantidade de entrada maior que zero."));
        mvc.perform(post("/stock/receipts").param("quantity", "3"))
                .andExpect(redirectedUrl("/stock"))
                .andExpect(flash().attribute("alert", "Escolha o produto da entrada."));

        assertThat(stock(id)).isEqualTo(5);
        assertThat(movements(id)).hasSize(1);
    }

    @Test
    void adjustmentSetsTheCountedQuantityAndRecordsTheDifference() throws Exception {
        long id = createProduct("CN-01", 10);

        mvc.perform(post("/stock/adjustments").param("productId", String.valueOf(id)).param("counted", "7")
                        .param("reason", "Inventário de outubro"))
                .andExpect(flash().attribute("notice", "Estoque de Caneta ajustado para 7 (-3)."));
        mvc.perform(post("/stock/adjustments").param("productId", String.valueOf(id)).param("counted", "9")
                        .param("reason", "Achei uma caixa"))
                .andExpect(flash().attribute("notice", "Estoque de Caneta ajustado para 9 (+2)."));

        assertThat(stock(id)).isEqualTo(9);
        assertThat(movements(id)).extracting(m -> m.get("quantity")).containsExactly(10, -3, 2);
    }

    @Test
    void adjustmentNeedsAReasonAndAChange() throws Exception {
        long id = createProduct("CN-01", 10);

        mvc.perform(post("/stock/adjustments").param("productId", String.valueOf(id)).param("counted", "7"))
                .andExpect(flash().attribute("alert", "Informe o motivo do ajuste."));
        mvc.perform(post("/stock/adjustments").param("productId", String.valueOf(id)).param("counted", "10")
                        .param("reason", "Conferência"))
                .andExpect(flash().attribute("alert", "O estoque de Caneta já é 10."));
        mvc.perform(post("/stock/adjustments").param("productId", String.valueOf(id)).param("counted", "-1")
                        .param("reason", "x"))
                .andExpect(flash().attribute("alert", "Informe a quantidade contada (zero ou mais)."));

        assertThat(stock(id)).isEqualTo(10);
        assertThat(movements(id)).hasSize(1);
    }

    @Test
    void saleAndCancellationAreInTheLedgerWithTheSale() throws Exception {
        long id = createProduct("CN-01", 10);
        String location = mvc.perform(post("/sales").param("items[0].productId", String.valueOf(id))
                .param("items[0].quantity", "4")).andReturn().getResponse().getHeader("Location");
        long saleId = Long.parseLong(location.substring("/sales/".length()));
        mvc.perform(post("/sales/{id}/cancel", saleId).param("reason", "Cliente desistiu"));

        List<Map<String, Object>> ledger = movements(id);
        assertThat(ledger).extracting(m -> m.get("type")).containsExactly("INITIAL", "SALE", "CANCELLATION");
        assertThat(ledger).extracting(m -> m.get("quantity")).containsExactly(10, -4, 4);
        assertThat(ledger).extracting(m -> m.get("balance_after")).containsExactly(10, 6, 10);
        assertThat(ledger.get(1)).containsEntry("sale_id", saleId);
        assertThat(ledger.get(2)).containsEntry("reason", "Cliente desistiu");
    }

    @Test
    void ledgerAlwaysEndsAtTheCurrentStock() throws Exception {
        long id = createProduct("CN-01", 10);
        mvc.perform(post("/stock/receipts").param("productId", String.valueOf(id)).param("quantity", "5"));
        mvc.perform(post("/sales").param("items[0].productId", String.valueOf(id)).param("items[0].quantity", "3"));
        mvc.perform(post("/stock/adjustments").param("productId", String.valueOf(id)).param("counted", "11")
                .param("reason", "Perda"));

        Integer sum = jdbc.queryForObject("select sum(quantity) from stock_movements where product_id = ?", Integer.class, id);
        assertThat(sum).isEqualTo(stock(id)).isEqualTo(11);
    }

    @Test
    void pageShowsHistoryAndFilters() throws Exception {
        long pen = createProduct("CN-01", 10);
        long bag = createProduct("MC-01", 3);
        mvc.perform(post("/stock/receipts").param("productId", String.valueOf(pen)).param("quantity", "5")
                .param("reason", "NF 99"));

        mvc.perform(get("/stock"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Histórico de movimentações")))
                .andExpect(content().string(containsString("NF 99")))
                .andExpect(content().string(containsString("+5")));
        mvc.perform(get("/stock").param("productId", String.valueOf(bag)))
                .andExpect(content().string(containsString("Histórico de Caneta MC-01")))
                .andExpect(content().string(not(containsString("NF 99"))));
        mvc.perform(get("/stock").param("type", "RECEIPT"))
                .andExpect(content().string(containsString("NF 99")))
                .andExpect(content().string(not(containsString(">Estoque inicial</span>"))));
        mvc.perform(get("/stock").param("type", "nonsense")).andExpect(status().isOk());
    }

    @Test
    void editFormShowsStockReadOnlyWithLinkToTheLedger() throws Exception {
        long id = createProduct("CN-01", 10);

        mvc.perform(get("/products/{id}/edit", id))
                .andExpect(content().string(not(containsString("name=\"stockQuantity\""))))
                .andExpect(content().string(containsString("href=\"/stock?productId=" + id + "\"")));
    }

    @Test
    void deletingANeverSoldProductTakesItsLedgerAlong() throws Exception {
        long id = createProduct("CN-01", 10);
        mvc.perform(post("/stock/receipts").param("productId", String.valueOf(id)).param("quantity", "5"));

        mvc.perform(post("/products/{id}/delete", id)).andExpect(flash().attribute("notice", "Produto excluído."));

        assertThat(movements(id)).isEmpty();
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(post("/stock/receipts").param("productId", "999999").param("quantity", "1"))
                .andExpect(status().isNotFound());
    }

    private long createProduct(String sku, int stock) throws Exception {
        String name = sku.equals("CN-01") ? "Caneta" : "Caneta " + sku;
        mvc.perform(post("/products").param("name", name).param("sku", sku).param("price", "2.50")
                .param("stockQuantity", String.valueOf(stock)).param("active", "true"));
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    private int stock(long productId) {
        return jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, productId);
    }

    private List<Map<String, Object>> movements(long productId) {
        return jdbc.queryForList("""
                select type, quantity, balance_after, reason, sale_id, created_by
                from stock_movements where product_id = ? order by id""", productId);
    }
}
