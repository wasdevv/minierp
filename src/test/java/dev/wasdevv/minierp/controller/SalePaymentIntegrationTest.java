package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesRegex;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Payment method, discount and cash change on a sale of 4 × R$ 12,50 = R$ 50,00. */
class SalePaymentIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long productId;
    private long customerId;

    @BeforeEach
    void seed() {
        productId = jdbc.queryForObject("""
                insert into products (name, sku, price, stock_quantity, created_at, updated_at)
                values ('Caderno', 'CD-01', 12.50, 100, now(), now()) returning id""", Long.class);
        customerId = jdbc.queryForObject("""
                insert into customers (name, created_at, updated_at) values ('Maria Souza', now(), now()) returning id""",
                Long.class);
    }

    @Test
    void discountInReaisAndPixPayment() throws Exception {
        Map<String, Object> sale = register(sale("PIX").param("discountValue", "7.50"));

        assertThat(sale).containsEntry("payment_method", "PIX");
        assertAmounts(sale, "50.00", "7.50", "42.50");
        assertThat(sale.get("amount_received")).isNull();
    }

    @Test
    void percentDiscountRoundsHalfUpToTheCent() throws Exception {
        Map<String, Object> sale = register(sale("DEBIT_CARD").param("discountType", "PERCENT").param("discountValue", "3.33"));

        assertAmounts(sale, "50.00", "1.67", "48.33");
    }

    @Test
    void cashWithChange() throws Exception {
        Map<String, Object> sale = register(sale("CASH").param("discountValue", "5").param("amountReceived", "50"));

        assertAmounts(sale, "50.00", "5.00", "45.00");
        assertThat((BigDecimal) sale.get("amount_received")).isEqualByComparingTo("50.00");
        assertThat((BigDecimal) sale.get("change_amount")).isEqualByComparingTo("5.00");
    }

    @Test
    void receivedAmountIsIgnoredForOtherMethods() throws Exception {
        Map<String, Object> sale = register(sale("CREDIT_CARD").param("amountReceived", "100"));

        assertThat(sale.get("amount_received")).isNull();
        assertThat(sale.get("change_amount")).isNull();
    }

    @Test
    void onAccountNeedsACustomer() throws Exception {
        reject(sale("ON_ACCOUNT"), "Venda fiado precisa de um cliente, para saber quem vai pagar.");

        Map<String, Object> sale = register(sale("ON_ACCOUNT").param("customerId", String.valueOf(customerId)));
        assertThat(sale).containsEntry("payment_method", "ON_ACCOUNT");
    }

    @Test
    void rejectsMissingMethodBadDiscountAndShortCash() throws Exception {
        reject(post("/sales").param("items[0].productId", String.valueOf(productId)).param("items[0].quantity", "4"),
                "Escolha a forma de pagamento.");
        reject(sale("PIX").param("discountValue", "50.01"), "O desconto não pode passar do subtotal da venda.");
        reject(sale("PIX").param("discountType", "PERCENT").param("discountValue", "101"), "O desconto pode ser de no máximo 100%.");
        reject(sale("PIX").param("discountValue", "-1"), "O desconto não pode ser negativo.");
        reject(sale("CASH").param("amountReceived", "49.99"), "O valor recebido (R$ 49,99) é menor que o total (R$ 50,00).");
        reject(sale("PIX").param("discountValue", "abc"), "Informe o desconto como número (ex.: 5 ou 5.50).");
        reject(sale("BITCOIN"), "Escolha uma forma de pagamento da lista.");

        assertThat(jdbc.queryForObject("select count(*) from sales", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, productId))
                .as("rejected sales leave the stock alone").isEqualTo(100);
    }

    @Test
    void rejectsMoreThanTwoDecimalPlacesAndAbsurdCash() throws Exception {
        reject(sale("PIX").param("discountValue", "1.005"), "Use no máximo 2 casas decimais no desconto.");
        reject(sale("CASH").param("amountReceived", "60.001"), "Use no máximo 2 casas decimais no valor recebido.");
        reject(sale("CASH").param("amountReceived", "99999999999"), "O valor recebido é grande demais.");
        reject(sale("CASH").param("amountReceived", "x"), "Informe o valor recebido como número (ex.: 50 ou 50.00).");
    }

    @Test
    void trailingZerosAreNotExtraDecimalPlaces() throws Exception {
        assertAmounts(register(sale("PIX").param("discountValue", "2.500")), "50.00", "2.50", "47.50");
    }

    @Test
    void unknownDiscountTypeMeansReais() throws Exception {
        assertAmounts(register(sale("PIX").param("discountType", "WHATEVER").param("discountValue", "10")), "50.00", "10.00", "40.00");
    }

    @Test
    void fullDiscountMakesAFreeSale() throws Exception {
        assertAmounts(register(sale("PIX").param("discountType", "PERCENT").param("discountValue", "100")), "50.00", "50.00", "0.00");
    }

    @Test
    void detailShowsDiscountPaymentAndChange() throws Exception {
        long id = id(register(sale("CASH").param("discountValue", "5").param("amountReceived", "50")));

        mvc.perform(get("/sales/{id}", id))
                .andExpect(content().string(containsString("Subtotal")))
                .andExpect(content().string(containsString("− R$ 5,00")))
                .andExpect(content().string(containsString("R$ 45,00")))
                .andExpect(content().string(containsString("Dinheiro")))
                .andExpect(content().string(containsString("Troco")));
        mvc.perform(get("/sales")).andExpect(content().string(containsString("Dinheiro")));
    }

    @Test
    void reportAndDashboardCountTheTotalAfterDiscount() throws Exception {
        register(sale("PIX").param("discountValue", "10"));
        String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

        mvc.perform(get("/reports/sales").param("from", today).param("to", today))
                .andExpect(content().string(containsString("R$ 40,00")));
        mvc.perform(get("/")).andExpect(content().string(containsString("R$ 40,00")));
    }

    @Test
    void formKeepsWhatWasTypedOnError() throws Exception {
        mvc.perform(sale("CASH").param("amountReceived", "10").param("discountValue", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesRegex("(?s).*value=\"CASH\"[^>]*checked=\"checked\".*")))
                .andExpect(content().string(containsString("value=\"10\"")))
                .andExpect(content().string(containsString("− R$ 2,00")));
    }

    private MockHttpServletRequestBuilder sale(String method) {
        return post("/sales").param("paymentMethod", method)
                .param("items[0].productId", String.valueOf(productId)).param("items[0].quantity", "4");
    }

    private Map<String, Object> register(MockHttpServletRequestBuilder request) throws Exception {
        String location = mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn().getResponse()
                .getHeader("Location");
        long id = Long.parseLong(location.substring("/sales/".length()));
        return jdbc.queryForMap("select * from sales where id = ?", id);
    }

    private void reject(MockHttpServletRequestBuilder request, String message) throws Exception {
        mvc.perform(request).andExpect(status().isOk()).andExpect(content().string(containsString(message)));
    }

    private static long id(Map<String, Object> sale) {
        return ((Number) sale.get("id")).longValue();
    }

    private static void assertAmounts(Map<String, Object> sale, String subtotal, String discount, String total) {
        assertThat((BigDecimal) sale.get("subtotal_amount")).isEqualByComparingTo(subtotal);
        assertThat((BigDecimal) sale.get("discount_amount")).isEqualByComparingTo(discount);
        assertThat((BigDecimal) sale.get("total_amount")).isEqualByComparingTo(total);
    }
}
