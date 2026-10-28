package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import dev.wasdevv.minierp.support.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Cost price, margin, per-product minimum stock and the month's gross profit. */
class ProductCostIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void costAndMinimumAreSavedAndTheMarginIsShown() throws Exception {
        long id = create("Caderno", "CD-01", "20.00", "12.50", 50, 15);

        assertThat(jdbc.queryForObject("select cost_price from products where id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("12.50");
        assertThat(jdbc.queryForObject("select min_stock from products where id = ?", Integer.class, id)).isEqualTo(15);
        mvc.perform(get("/products")).andExpect(content().string(containsString("37,5%")));
        mvc.perform(get("/products/{id}/edit", id))
                .andExpect(content().string(containsString("value=\"12.50\"")))
                .andExpect(content().string(containsString("value=\"15\"")));
    }

    @Test
    void withoutCostTheMarginIsUnknownNotOneHundredPercent() throws Exception {
        create("Caderno", "CD-01", "20.00", "", 50, 10);

        mvc.perform(get("/products"))
                .andExpect(content().string(containsString("Sem custo cadastrado")))
                .andExpect(content().string(not(containsString("100,0%"))));
    }

    @Test
    void rejectsNegativeCostAndMinimum() throws Exception {
        mvc.perform(post("/products").param("name", "X").param("sku", "X-1").param("price", "1").param("stockQuantity", "1")
                        .param("costPrice", "-1").param("minStock", "-2"))
                .andExpect(model().attributeHasFieldErrors("product", "costPrice", "minStock"));
    }

    @Test
    void lowStockFollowsEachProductsMinimum() throws Exception {
        create("Lápis", "LP-01", "1.20", "0.40", 40, 50);     // 40 ≤ 50: low
        create("Mochila", "MC-01", "129.00", "70.00", 3, 2);  // 3 > 2: fine

        mvc.perform(get("/products").param("filter", "low-stock"))
                .andExpect(content().string(containsString("Lápis")))
                .andExpect(content().string(not(containsString("Mochila"))));
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("40 / mín. 50")))
                .andExpect(content().string(not(containsString("MC-01</span>"))));
    }

    @Test
    void saleKeepsTheCostItHadAndTheDashboardShowsTheProfit() throws Exception {
        long notebook = create("Caderno", "CD-01", "20.00", "12.50", 50, 5);
        sell(notebook, 4, "8.00");  // revenue 80, cost 50, discount 8 → profit 22

        mvc.perform(post("/products/{id}", notebook).param("version", "1").param("name", "Caderno").param("sku", "CD-01")
                .param("price", "20.00").param("costPrice", "19.00").param("minStock", "5").param("active", "true"));

        assertThat(jdbc.queryForObject("select unit_cost from sale_items", BigDecimal.class)).isEqualByComparingTo("12.50");
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Lucro bruto no mês")))
                .andExpect(content().string(containsString("R$ 22,00")))
                .andExpect(content().string(containsString("total pago menos o custo dos itens")));
    }

    @Test
    void profitIsMarkedPartialWhenSomeSoldItemHasNoCost() throws Exception {
        long withCost = create("Caderno", "CD-01", "20.00", "12.50", 50, 5);
        long noCost = create("Borracha", "BR-01", "1.00", "", 50, 5);
        sell(withCost, 1, "0");
        sell(noCost, 3, "0");

        mvc.perform(get("/"))
                .andExpect(content().string(containsString("R$ 7,50")))
                .andExpect(content().string(containsString("parcial: 1 venda(s) com item sem custo ficaram de fora")));
    }

    @Test
    void withoutAnyCostTheProfitIsUnknownNotZeroOrNegative() throws Exception {
        long noCost = create("Borracha", "BR-01", "1.00", "", 50, 5);
        sell(noCost, 3, "0.50");

        mvc.perform(get("/"))
                .andExpect(content().string(containsString("cadastre o custo dos produtos para ver o lucro")))
                .andExpect(content().string(not(containsString("−R$ 0,50"))))
                .andExpect(content().string(not(containsString("R$ -"))));
    }

    @Test
    void lossIsShownWithAMinusSign() throws Exception {
        long id = create("Caderno", "CD-01", "20.00", "12.50", 50, 5);
        sell(id, 1, "10.00");  // paid 10, cost 12.50

        mvc.perform(get("/")).andExpect(content().string(containsString("−R$ 2,50")));
    }

    private long create(String name, String sku, String price, String cost, int stock, int min) throws Exception {
        mvc.perform(post("/products").param("name", name).param("sku", sku).param("price", price).param("costPrice", cost)
                .param("stockQuantity", String.valueOf(stock)).param("minStock", String.valueOf(min)).param("active", "true"));
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    private void sell(long productId, int quantity, String discount) throws Exception {
        mvc.perform(post("/sales").param("paymentMethod", "PIX").param("discountValue", discount)
                .param("items[0].productId", String.valueOf(productId)).param("items[0].quantity", String.valueOf(quantity)));
    }
}
