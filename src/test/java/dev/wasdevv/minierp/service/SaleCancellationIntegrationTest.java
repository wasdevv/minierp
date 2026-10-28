package dev.wasdevv.minierp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.dto.SaleForm;
import dev.wasdevv.minierp.dto.SaleItemForm;
import dev.wasdevv.minierp.exception.SaleRejectedException;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** README rule 7: cancelling puts the stock back and takes the sale out of the reports. */
class SaleCancellationIntegrationTest extends IntegrationTest {

    @Autowired
    private SaleService sales;

    @Autowired
    private ProductRepository products;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cancellingPutsEveryItemBackInStock() throws Exception {
        Product pen = product("Caneta", "CN-01", 10, true);
        Product bag = product("Mochila", "MC-01", 3, true);
        Sale sale = sales.create(form(line(pen, 4), line(bag, 3)));

        mvc.perform(post("/sales/{id}/cancel", sale.getId()).param("reason", "  Cliente desistiu  "))
                .andExpect(redirectedUrl("/sales/" + sale.getId()))
                .andExpect(flash().attribute("notice", "Venda cancelada. Os itens voltaram para o estoque."));

        assertThat(stockOf(pen)).isEqualTo(10);
        assertThat(stockOf(bag)).isEqualTo(3);
        assertThat(jdbc.queryForMap("select status, cancel_reason, cancelled_at from sales where id = ?", sale.getId()))
                .containsEntry("status", "CANCELLED")
                .containsEntry("cancel_reason", "Cliente desistiu")
                .extractingByKey("cancelled_at").isNotNull();
    }

    @Test
    void inactiveProductStillGetsItsStockBack() {
        Product pen = product("Caneta", "CN-01", 10, true);
        Sale sale = sales.create(form(line(pen, 4)));
        jdbc.update("update products set active = false where id = ?", pen.getId());

        sales.cancel(sale.getId(), "Devolução");

        assertThat(stockOf(pen)).isEqualTo(10);
    }

    @Test
    void cancellingTwiceIsRefusedAndReturnsStockOnce() throws Exception {
        Product pen = product("Caneta", "CN-01", 10, true);
        Sale sale = sales.create(form(line(pen, 4)));
        sales.cancel(sale.getId(), "Erro de digitação");

        mvc.perform(post("/sales/{id}/cancel", sale.getId()).param("reason", "De novo"))
                .andExpect(flash().attribute("alert", "Esta venda já foi cancelada."));

        assertThat(stockOf(pen)).isEqualTo(10);
    }

    @Test
    void concurrentCancellationsReturnTheStockOnlyOnce() throws Exception {
        Product pen = product("Caneta", "CN-01", 10, true);
        Sale sale = sales.create(form(line(pen, 4)));
        int clerks = 4;

        ExecutorService pool = Executors.newFixedThreadPool(clerks);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < clerks; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    sales.cancel(sale.getId(), "Duplicado");
                } catch (RuntimeException e) {
                    failures.add(e);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(stockOf(pen)).isEqualTo(10);
        assertThat(failures).hasSize(clerks - 1)
                .allSatisfy(e -> assertThat(e).isInstanceOf(SaleRejectedException.class).hasMessage("Esta venda já foi cancelada."));
    }

    @Test
    void reasonIsRequiredAndBounded() throws Exception {
        Product pen = product("Caneta", "CN-01", 10, true);
        Sale sale = sales.create(form(line(pen, 4)));

        mvc.perform(post("/sales/{id}/cancel", sale.getId()).param("reason", "   "))
                .andExpect(flash().attribute("alert", "Informe o motivo do cancelamento."));
        assertThatThrownBy(() -> sales.cancel(sale.getId(), "x".repeat(201)))
                .hasMessage("O motivo pode ter no máximo 200 caracteres.");

        assertThat(stockOf(pen)).isEqualTo(6);
    }

    @Test
    void missingSaleIs404() throws Exception {
        mvc.perform(post("/sales/{id}/cancel", 999_999).param("reason", "x")).andExpect(status().isNotFound());
    }

    @Test
    void detailShowsTheCancellationAndHidesTheCancelForm() throws Exception {
        Product pen = product("Caneta", "CN-01", 10, true);
        Sale open = sales.create(form(line(pen, 1)));
        Sale cancelled = sales.create(form(line(pen, 1)));
        sales.cancel(cancelled.getId(), "Cliente desistiu");

        mvc.perform(get("/sales/{id}", open.getId()))
                .andExpect(content().string(containsString("action=\"/sales/" + open.getId() + "/cancel\"")))
                .andExpect(content().string(containsString("class=\"pill\">Confirmada")));
        mvc.perform(get("/sales/{id}", cancelled.getId()))
                .andExpect(content().string(containsString("Motivo: Cliente desistiu")))
                .andExpect(content().string(containsString("pill pill--danger\">Cancelada")))
                .andExpect(content().string(not(containsString("/cancel\""))));
        mvc.perform(get("/sales"))
                .andExpect(content().string(containsString("Cancelada")))
                .andExpect(content().string(containsString("class=\"num struck\"")));
    }

    @Test
    void reportLeavesCancelledSalesOut() throws Exception {
        Product pen = product("Caneta", "CN-01", 100, true);
        sales.create(form(line(pen, 2)));
        Sale cancelled = sales.create(form(line(pen, 40)));
        sales.cancel(cancelled.getId(), "Erro");
        String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

        mvc.perform(get("/reports/sales").param("from", today).param("to", today))
                .andExpect(content().string(containsString("R$ 5,00")))
                .andExpect(content().string(not(containsString("R$ 100,00"))))
                .andExpect(content().string(not(containsString("#" + cancelled.getId() + "<"))));
    }

    private int stockOf(Product product) {
        return jdbc.queryForObject("select stock_quantity from products where id = ?", Integer.class, product.getId());
    }

    private static SaleForm form(SaleItemForm... lines) {
        SaleForm form = new SaleForm();
        form.setItems(new ArrayList<>(List.of(lines)));
        return form;
    }

    private static SaleItemForm line(Product product, int quantity) {
        return new SaleItemForm(product.getId(), quantity);
    }

    private Product product(String name, String sku, int stock, boolean active) {
        Product product = new Product();
        product.setName(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal("2.50"));
        product.setStockQuantity(stock);
        product.setActive(active);
        return products.save(product);
    }
}
