package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import dev.wasdevv.minierp.model.Customer;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.repository.CustomerRepository;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.repository.SaleRepository;
import dev.wasdevv.minierp.support.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SaleControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private SaleRepository sales;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void recordsSaleWithTotalsComputedOnTheServer() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", true);
        Product notebook = product("Caderno", "CD-96", "14.90", true);
        Customer maria = customer("Maria Souza");

        mvc.perform(post("/sales")
                        .param("customerId", maria.getId().toString())
                        .param("items[0].productId", pen.getId().toString())
                        .param("items[0].quantity", "4")
                        .param("items[1].productId", notebook.getId().toString())
                        .param("items[1].quantity", "3")
                        // Fields the form does not have; a tampered request must not set prices or totals.
                        .param("totalAmount", "0.01")
                        .param("items[0].unitPrice", "0.01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/sales/")))
                .andExpect(flash().attribute("notice", "Venda registrada."));

        Sale sale = sales.findAll().getFirst();
        assertThat(jdbc.queryForObject("select total_amount from sales where id = ?", BigDecimal.class, sale.getId()))
                .isEqualByComparingTo("54.70");
        assertThat(jdbc.queryForList("select unit_price from sale_items where sale_id = ? order by id",
                BigDecimal.class, sale.getId())).extracting(BigDecimal::toPlainString).containsExactly("2.50", "14.90");
        assertThat(jdbc.queryForObject("select customer_id from sales where id = ?", Long.class, sale.getId()))
                .isEqualTo(maria.getId());
    }

    @Test
    void showsSaleDetail() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", true);
        Long id = createSale(null, pen, 4);

        mvc.perform(get("/sales/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Venda #" + id)))
                .andExpect(content().string(containsString("Consumidor final")))
                .andExpect(content().string(containsString("CN-01")))
                .andExpect(content().string(containsString("R$ 10,00")));
    }

    @Test
    void keepsThePriceChargedWhenTheProductPriceChanges() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", true);
        Long id = createSale(null, pen, 2);

        Product current = products.findById(pen.getId()).orElseThrow();
        current.setPrice(new BigDecimal("9.99"));
        products.save(current);

        mvc.perform(get("/sales/{id}", id))
                .andExpect(content().string(containsString("R$ 2,50")))
                .andExpect(content().string(containsString("R$ 5,00")))
                .andExpect(content().string(not(containsString("R$ 9,99"))));
    }

    @Test
    void rejectsSaleWithoutItems() throws Exception {
        mvc.perform(post("/sales").param("items[0].productId", "").param("items[0].quantity", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("sales/form"))
                .andExpect(content().string(containsString("Adicione ao menos um item à venda.")));

        assertThat(sales.count()).isZero();
    }

    @Test
    void rejectsInvalidQuantitiesAndKeepsWhatWasTyped() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", true);

        mvc.perform(post("/sales")
                        .param("items[0].productId", pen.getId().toString())
                        .param("items[0].quantity", "0"))
                .andExpect(content().string(containsString("Item 1: a quantidade deve ser maior que zero.")))
                .andExpect(content().string(containsString("value=\"0\"")));

        mvc.perform(post("/sales")
                        .param("items[0].productId", pen.getId().toString())
                        .param("items[0].quantity", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Item 1: a quantidade deve ser um número inteiro.")));

        assertThat(sales.count()).isZero();
    }

    @Test
    void tamperedProductAndCustomerIdsGetReadableMessages() throws Exception {
        mvc.perform(post("/sales")
                        .param("customerId", "abc")
                        .param("items[0].productId", "x")
                        .param("items[0].quantity", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Item 1: escolha um produto da lista.")))
                .andExpect(content().string(containsString("Escolha um cliente da lista.")));

        assertThat(sales.count()).isZero();
    }

    @Test
    void removingTheLastRowLeavesAnEmptyOne() throws Exception {
        product("Caneta", "CN-01", "2.50", true);

        mvc.perform(post("/sales").param("removeItem", "0").param("items[0].productId", "").param("items[0].quantity", ""))
                .andExpect(content().string(containsString("name=\"items[0].productId\"")));
    }

    @Test
    void rejectsInactiveProduct() throws Exception {
        Product old = product("Caneta antiga", "CN-00", "1.00", false);

        mvc.perform(post("/sales")
                        .param("items[0].productId", old.getId().toString())
                        .param("items[0].quantity", "1"))
                .andExpect(content().string(containsString("O produto Caneta antiga está inativo e não pode ser vendido.")));

        assertThat(sales.count()).isZero();
    }

    @Test
    void formOffersOnlyActiveProducts() throws Exception {
        product("Caneta", "CN-01", "2.50", true);
        product("Caneta antiga", "CN-00", "1.00", false);

        mvc.perform(get("/sales/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Caneta · CN-01 · R$ 2,50")))
                .andExpect(content().string(not(containsString("Caneta antiga"))))
                .andExpect(content().string(containsString("aria-current=\"page\">Vendas")));
    }

    @Test
    void addAndRemoveRowsWithoutSaving() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", true);

        mvc.perform(post("/sales")
                        .param("addItem", "1")
                        .param("items[0].productId", pen.getId().toString())
                        .param("items[0].quantity", "3"))
                .andExpect(view().name("sales/form"))
                .andExpect(content().string(containsString("name=\"items[1].productId\"")))
                .andExpect(content().string(containsString("R$ 7,50")));

        mvc.perform(post("/sales")
                        .param("removeItem", "0")
                        .param("items[0].productId", pen.getId().toString())
                        .param("items[0].quantity", "3")
                        .param("items[1].productId", "")
                        .param("items[1].quantity", ""))
                .andExpect(content().string(not(containsString("name=\"items[1].productId\""))))
                .andExpect(content().string(containsString("R$ 0,00")));

        assertThat(sales.count()).isZero();
    }

    @Test
    void listsNewestFirstWithCustomerAndUnits() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", true);
        Customer maria = customer("Maria Souza");
        Long first = createSale(null, pen, 1);
        Long second = createSale(maria, pen, 3);

        String html = mvc.perform(get("/sales")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat(html.indexOf("#" + second)).isLessThan(html.indexOf("#" + first));
        assertThat(html).contains("Maria Souza", "Consumidor final", "R$ 7,50", "2 venda(s)");
    }

    @Test
    void missingSaleIs404() throws Exception {
        mvc.perform(get("/sales/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void soldProductCannotBeDeleted() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", true);
        createSale(null, pen, 1);

        mvc.perform(post("/products/{id}/delete", pen.getId()))
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attribute("alert", containsString("já foi vendido")));

        assertThat(products.existsById(pen.getId())).isTrue();
    }

    @Test
    void customerWithSalesCannotBeDeleted() throws Exception {
        Product pen = product("Caneta", "CN-01", "2.50", true);
        Customer maria = customer("Maria Souza");
        createSale(maria, pen, 1);

        mvc.perform(post("/customers/{id}/delete", maria.getId()))
                .andExpect(redirectedUrl("/customers"))
                .andExpect(flash().attribute("alert", containsString("tem vendas registradas")));

        assertThat(customers.existsById(maria.getId())).isTrue();
    }

    @Test
    void alertIsShownOnTheList() throws Exception {
        mvc.perform(get("/products").flashAttr("alert", "Mensagem de teste"))
                .andExpect(content().string(containsString("class=\"alert\" role=\"alert\">Mensagem de teste")));
    }

    private Long createSale(Customer customer, Product product, int quantity) throws Exception {
        var request = post("/sales")
                .param("items[0].productId", product.getId().toString())
                .param("items[0].quantity", String.valueOf(quantity));
        if (customer != null) {
            request.param("customerId", customer.getId().toString());
        }
        String location = mvc.perform(request).andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring("/sales/".length()));
    }

    private Product product(String name, String sku, String price, boolean active) {
        Product product = new Product();
        product.setName(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(100);
        product.setActive(active);
        return products.save(product);
    }

    private Customer customer(String name) {
        Customer customer = new Customer();
        customer.setName(name);
        return customers.save(customer);
    }
}
