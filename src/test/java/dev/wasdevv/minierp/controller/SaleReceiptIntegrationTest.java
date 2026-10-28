package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.service.SaleReceipt;
import dev.wasdevv.minierp.service.SaleService;
import dev.wasdevv.minierp.support.IntegrationTest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Receipt page and WhatsApp message of a sale: 3 × Caderno at R$ 14,90, R$ 4,70 off, paid in cash with 50. */
class SaleReceiptIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SaleService sales;

    @Autowired
    private SaleReceipt receipt;

    private long productId;

    @BeforeEach
    void seed() {
        productId = jdbc.queryForObject("""
                insert into products (name, sku, price, stock_quantity, created_at, updated_at)
                values ('Caderno', 'CD-96', 14.90, 100, now(), now()) returning id""", Long.class);
    }

    @Test
    void receiptPageHasItemsTotalsPaymentAndPrintButton() throws Exception {
        long id = sell(null);

        mvc.perform(get("/sales/{id}/receipt", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Venda #" + id)))
                .andExpect(content().string(containsString("3 x 14,90")))
                .andExpect(content().string(containsString("R$ 44,70")))
                .andExpect(content().string(containsString("− R$ 4,70")))
                .andExpect(content().string(containsString("R$ 40,00")))
                .andExpect(content().string(containsString("Dinheiro")))
                .andExpect(content().string(containsString("R$ 10,00")))
                .andExpect(content().string(containsString("window.print()")))
                .andExpect(content().string(containsString("Não é documento fiscal.")));
    }

    @Test
    void whatsAppMessageWithoutCustomerLetsWhatsAppAskWhom() throws Exception {
        long id = sell(null);

        String url = receipt.whatsAppUrl(sales.get(id));

        assertThat(url).startsWith("https://wa.me/?text=").doesNotContain("+");
        assertThat(URLDecoder.decode(url.substring(url.indexOf("text=") + 5), StandardCharsets.UTF_8))
                .contains("*Mini ERP*", "Venda #" + id, "3x Caderno — R$ 44,70", "Desconto: −R$ 4,70",
                        "*Total: R$ 40,00*", "Pagamento: Dinheiro", "Recebido: R$ 50,00 · Troco: R$ 10,00");
    }

    @Test
    void whatsAppGoesToTheCustomersNumber() throws Exception {
        long customer = jdbc.queryForObject("""
                insert into customers (name, phone, created_at, updated_at)
                values ('Maria Souza', '11987654321', now(), now()) returning id""", Long.class);
        long id = sell(customer);

        mvc.perform(get("/sales/{id}", id))
                .andExpect(content().string(containsString("href=\"https://wa.me/5511987654321?text=")))
                .andExpect(content().string(containsString("WhatsApp para (11) 98765-4321")))
                .andExpect(content().string(containsString("href=\"/sales/" + id + "/receipt\"")));
        mvc.perform(get("/sales/{id}/receipt", id)).andExpect(content().string(containsString("Cliente: Maria Souza")));
    }

    @Test
    void cancelledSaleSaysSoOnTheReceiptAndInTheMessage() throws Exception {
        long id = sell(null);
        sales.cancel(id, "Erro");

        mvc.perform(get("/sales/{id}/receipt", id)).andExpect(content().string(containsString("VENDA CANCELADA")));
        assertThat(receipt.text(sales.get(id))).contains("*VENDA CANCELADA*");
    }

    @Test
    void missingSaleIs404() throws Exception {
        mvc.perform(get("/sales/{id}/receipt", 999_999)).andExpect(status().isNotFound());
    }

    private long sell(Long customerId) throws Exception {
        var request = post("/sales").param("paymentMethod", "CASH").param("discountValue", "4.70")
                .param("amountReceived", "50")
                .param("items[0].productId", String.valueOf(productId)).param("items[0].quantity", "3");
        if (customerId != null) {
            request.param("customerId", String.valueOf(customerId));
        }
        String location = mvc.perform(request).andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring("/sales/".length()));
    }
}
