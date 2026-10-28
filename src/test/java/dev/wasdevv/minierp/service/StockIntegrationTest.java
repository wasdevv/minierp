package dev.wasdevv.minierp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.dto.SaleForm;
import dev.wasdevv.minierp.dto.SaleItemForm;
import dev.wasdevv.minierp.exception.SaleRejectedException;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.repository.SaleRepository;
import dev.wasdevv.minierp.support.IntegrationTest;
import java.math.BigDecimal;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stock deduction against the real database: atomicity and concurrent sales. */
class StockIntegrationTest extends IntegrationTest {

    @Autowired
    private SaleService service;

    @Autowired
    private ProductRepository products;

    @Autowired
    private SaleRepository sales;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void saleTakesUnitsOutOfStock() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", 10);
        Product notebook = product("Caderno", "CD-96", "14.90", 4);

        mvc.perform(post("/sales")
                        .param("items[0].productId", pen.getId().toString())
                        .param("items[0].quantity", "3")
                        .param("items[1].productId", notebook.getId().toString())
                        .param("items[1].quantity", "4"))
                .andExpect(status().is3xxRedirection());

        assertThat(stockOf(pen)).isEqualTo(7);
        assertThat(stockOf(notebook)).isZero();
    }

    @Test
    void saleAboveStockIsRejectedAndChangesNothing() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", 10);
        Product notebook = product("Caderno", "CD-96", "14.90", 2);

        mvc.perform(post("/sales")
                        .param("items[0].productId", pen.getId().toString())
                        .param("items[0].quantity", "3")
                        .param("items[1].productId", notebook.getId().toString())
                        .param("items[1].quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Estoque insuficiente para Caderno: disponível 2, solicitado 5.")));

        assertThat(stockOf(pen)).isEqualTo(10);
        assertThat(stockOf(notebook)).isEqualTo(2);
        assertThat(sales.count()).isZero();
    }

    @Test
    void stockGoesBackWhenTheSaleInsertFails() {
        Product pen = product("Caneta", "CN-01", "13.37", 10);
        // Makes the INSERT INTO sales fail after the stock was already decremented in the same transaction.
        jdbc.execute("""
                CREATE FUNCTION test_reject_sale() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'rejected by test'; END $$""");
        jdbc.execute("""
                CREATE TRIGGER test_reject_sale BEFORE INSERT ON sales FOR EACH ROW
                WHEN (NEW.total_amount = 13.37) EXECUTE FUNCTION test_reject_sale()""");
        try {
            assertThatThrownBy(() -> service.create(form(line(pen, 1)))).isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER test_reject_sale ON sales");
            jdbc.execute("DROP FUNCTION test_reject_sale()");
        }

        assertThat(stockOf(pen)).isEqualTo(10);
        assertThat(sales.count()).isZero();
    }

    @Test
    void concurrentSalesDoNotLoseUpdates() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", 20);
        int buyers = 8;

        List<Throwable> failures = runConcurrently(buyers, () -> service.create(form(line(pen, 2))));

        assertThat(failures).isEmpty();
        assertThat(stockOf(pen)).isEqualTo(20 - buyers * 2);
        assertThat(sales.count()).isEqualTo(buyers);
    }

    @Test
    void concurrentSalesNeverOversell() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", 5);
        int buyers = 8;

        List<Throwable> failures = runConcurrently(buyers, () -> service.create(form(line(pen, 1))));

        assertThat(stockOf(pen)).isZero();
        assertThat(sales.count()).isEqualTo(5);
        assertThat(failures).hasSize(3).allSatisfy(failure -> assertThat(failure)
                .as("the loser gets the business message, not a constraint error")
                .isInstanceOf(SaleRejectedException.class)
                .hasMessageContaining("Estoque insuficiente"));
    }

    @Test
    void saleFormMarksOutOfStockProducts() throws Exception {
        product("Caneta", "CN-01", "2.50", 0);
        product("Caderno", "CD-96", "14.90", 8);

        mvc.perform(get("/sales/new"))
                .andExpect(content().string(containsString("disabled=\"disabled\">Caneta · CN-01 · R$ 2,50 · sem estoque")))
                .andExpect(content().string(containsString("Caderno · CD-96 · R$ 14,90 · 8 em estoque")));
    }

    /** Starts every task at the same moment and returns what they threw. */
    private static List<Throwable> runConcurrently(int count, Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    task.run();
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
        return failures;
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

    private Product product(String name, String sku, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        return products.save(product);
    }
}
