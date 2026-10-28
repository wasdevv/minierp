package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The customer page (/customers/{id}): contact, totals and purchase history. */
class CustomerPageIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long productId;

    @BeforeEach
    void seed() {
        productId = jdbc.queryForObject("""
                insert into products (name, sku, price, stock_quantity, created_at, updated_at)
                values ('Caderno', 'CD-01', 10.00, 100, now(), now()) returning id""", Long.class);
    }

    @Test
    void phoneIsNormalizedStoredAndShownWithWhatsAppLink() throws Exception {
        mvc.perform(post("/customers").param("name", "Maria Souza").param("phone", "+55 (11) 98765-4321"))
                .andExpect(redirectedUrl("/customers"));
        long id = jdbc.queryForObject("select id from customers", Long.class);

        assertThat(jdbc.queryForObject("select phone from customers where id = ?", String.class, id)).isEqualTo("11987654321");
        mvc.perform(get("/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("(11) 98765-4321")))
                .andExpect(content().string(containsString("href=\"https://wa.me/5511987654321\"")));
        mvc.perform(get("/customers/{id}/edit", id))
                .andExpect(content().string(containsString("value=\"(11) 98765-4321\"")));
        mvc.perform(get("/customers"))
                .andExpect(content().string(containsString("(11) 98765-4321")))
                .andExpect(content().string(containsString("href=\"/customers/" + id + "\"")));
    }

    @Test
    void invalidPhoneIsRejected() throws Exception {
        mvc.perform(post("/customers").param("name", "Maria").param("phone", "12345"))
                .andExpect(model().attributeHasFieldErrors("customer", "phone"))
                .andExpect(content().string(containsString("Informe o telefone com DDD")));
        mvc.perform(post("/customers").param("name", "Maria").param("phone", "abc"))
                .andExpect(model().attributeHasFieldErrors("customer", "phone"));

        assertThat(jdbc.queryForObject("select count(*) from customers", Integer.class)).isZero();
    }

    @Test
    void pageShowsTotalsOfConfirmedSalesAndTheWholeHistory() throws Exception {
        long id = customer("Maria Souza");
        long first = sell(id, "PIX", 2);        // R$ 20,00
        sell(id, "ON_ACCOUNT", 3);               // R$ 30,00 fiado
        long cancelled = sell(id, "PIX", 5);     // R$ 50,00, cancelled below
        mvc.perform(post("/sales/{id}/cancel", cancelled).param("reason", "Erro"));
        sell(customer("Outro"), "PIX", 9);       // someone else's

        mvc.perform(get("/customers/{id}", id))
                .andExpect(content().string(containsString("Histórico de compras")))
                .andExpect(content().string(containsString("R$ 50,00")))   // total comprado: 20 + 30
                .andExpect(content().string(containsString("R$ 25,00")))   // ticket médio
                .andExpect(content().string(containsString("R$ 30,00")))   // fiado
                .andExpect(content().string(containsString("#" + first)))
                .andExpect(content().string(containsString("#" + cancelled)))
                .andExpect(content().string(containsString("pill pill--danger\">Cancelada")))
                .andExpect(content().string(not(containsString("R$ 90,00"))));
    }

    @Test
    void customerWithoutSalesShowsAnEmptyHistory() throws Exception {
        mvc.perform(get("/customers/{id}", customer("Maria Souza")))
                .andExpect(content().string(containsString("Nenhuma compra registrada.")))
                .andExpect(content().string(containsString("nenhuma ainda")));
    }

    @Test
    void newSaleFromTheCustomerPageComesWithTheCustomerChosen() throws Exception {
        long id = customer("Maria Souza");

        mvc.perform(get("/sales/new").param("customerId", String.valueOf(id)))
                .andExpect(content().string(containsString("value=\"" + id + "\" selected=\"selected\"")));
    }

    @Test
    void editGoesBackToTheCustomerPage() throws Exception {
        long id = customer("Maria Souza");

        mvc.perform(post("/customers/{id}", id).param("name", "Maria S. Lima").param("phone", "11987654321"))
                .andExpect(redirectedUrl("/customers/" + id));
    }

    @Test
    void addressesThatUsedToBe405NowWork() throws Exception {
        long id = customer("Maria Souza");
        mvc.perform(get("/customers/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/products/{id}", productId)).andExpect(redirectedUrl("/products/" + productId + "/edit"));
        mvc.perform(get("/customers/{id}", 999_999)).andExpect(status().isNotFound());
    }

    private long customer(String name) {
        return jdbc.queryForObject("insert into customers (name, created_at, updated_at) values (?, now(), now()) returning id",
                Long.class, name);
    }

    private long sell(long customerId, String method, int quantity) throws Exception {
        String location = mvc.perform(post("/sales").param("customerId", String.valueOf(customerId))
                        .param("paymentMethod", method)
                        .param("items[0].productId", String.valueOf(productId))
                        .param("items[0].quantity", String.valueOf(quantity)))
                .andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring("/sales/".length()));
    }
}
