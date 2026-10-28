package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.support.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ProductControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private ProductRepository products;

    @Test
    void rootRedirectsToProducts() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/products"));
    }

    @Test
    void createsProductAndNormalizesInput() throws Exception {
        mvc.perform(post("/products")
                        .param("name", "  Caneta azul ")
                        .param("sku", " cn-01 ")
                        .param("description", "")
                        .param("price", "2.50")
                        .param("stockQuantity", "10")
                        .param("active", "true"))
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attribute("notice", "Produto cadastrado."));

        Product saved = products.findAll().getFirst();
        assertThat(saved.getName()).isEqualTo("Caneta azul");
        assertThat(saved.getSku()).isEqualTo("CN-01");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getPrice()).isEqualByComparingTo("2.50");
        assertThat(saved.getStockQuantity()).isEqualTo(10);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidProductWithFieldMessages() throws Exception {
        mvc.perform(post("/products")
                        .param("name", "")
                        .param("sku", "CN-01")
                        .param("price", "-1")
                        .param("stockQuantity", "-5"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/form"))
                .andExpect(model().attributeHasFieldErrors("product", "name", "price", "stockQuantity"))
                .andExpect(content().string(containsString("O preço não pode ser negativo.")))
                .andExpect(content().string(containsString("O estoque não pode ser negativo.")));

        assertThat(products.count()).isZero();
    }

    @Test
    void rejectsNonNumericPriceWithReadableMessage() throws Exception {
        mvc.perform(post("/products")
                        .param("name", "Caneta")
                        .param("sku", "CN-01")
                        .param("price", "abc")
                        .param("stockQuantity", "1"))
                .andExpect(model().attributeHasFieldErrorCode("product", "price", "typeMismatch"))
                .andExpect(content().string(containsString("Informe um preço válido")))
                .andExpect(content().string(not(containsString("NumberFormatException"))));
    }

    @Test
    void rejectsDuplicateSkuIgnoringCase() throws Exception {
        save("Caneta azul", "CN-01", "2.50", 10);

        mvc.perform(post("/products")
                        .param("name", "Outra caneta")
                        .param("sku", "cn-01")
                        .param("price", "3.00")
                        .param("stockQuantity", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("product", "sku", "duplicate"))
                .andExpect(content().string(containsString("Já existe um produto com este SKU.")));

        assertThat(products.count()).isEqualTo(1);
    }

    @Test
    void databaseRejectsNegativePriceEvenBypassingValidation() {
        Product product = new Product();
        product.setName("Direto no banco");
        product.setSku("DB-01");
        product.setPrice(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> products.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void editFormShowsCurrentValues() throws Exception {
        Product product = save("Caneta azul", "CN-01", "2.50", 10);

        mvc.perform(get("/products/{id}/edit", product.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Caneta azul\"")))
                .andExpect(content().string(containsString("value=\"CN-01\"")));
    }

    @Test
    void updatesProduct() throws Exception {
        Product product = save("Caneta azul", "CN-01", "2.50", 10);

        mvc.perform(post("/products/{id}", product.getId())
                        .param("version", "0")
                        .param("name", "Caneta preta")
                        .param("sku", "CN-01")
                        .param("price", "3.10")
                        .param("stockQuantity", "4")
                        .param("_active", "on"))
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attribute("notice", "Produto atualizado."));

        Product updated = products.findById(product.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Caneta preta");
        assertThat(updated.getPrice()).isEqualByComparingTo("3.10");
        assertThat(updated.getStockQuantity()).isEqualTo(4);
        assertThat(updated.isActive()).as("unchecked checkbox deactivates").isFalse();
    }

    @Test
    void updateRejectsSkuOfAnotherProduct() throws Exception {
        save("Caneta azul", "CN-01", "2.50", 10);
        Product other = save("Lápis", "LP-01", "1.00", 5);

        mvc.perform(post("/products/{id}", other.getId())
                        .param("version", "0")
                        .param("name", "Lápis")
                        .param("sku", "CN-01")
                        .param("price", "1.00")
                        .param("stockQuantity", "5"))
                .andExpect(model().attributeHasFieldErrorCode("product", "sku", "duplicate"));

        assertThat(products.findById(other.getId()).orElseThrow().getSku()).isEqualTo("LP-01");
    }

    @Test
    void editFormCarriesTheVersion() throws Exception {
        Product product = save("Caneta azul", "CN-01", "2.50", 10);

        mvc.perform(get("/products/{id}/edit", product.getId()))
                .andExpect(content().string(containsString("name=\"version\" value=\"0\"")));
    }

    @Test
    void staleEditDoesNotOverwriteStockChangedMeanwhile() throws Exception {
        Product product = save("Caneta azul", "CN-01", "2.50", 10);
        product.setStockQuantity(7);
        products.save(product);

        mvc.perform(post("/products/{id}", product.getId())
                        .param("version", "0")
                        .param("name", "Caneta azul")
                        .param("sku", "CN-01")
                        .param("price", "2.50")
                        .param("stockQuantity", "10")
                        .param("active", "true"))
                .andExpect(redirectedUrl("/products/" + product.getId() + "/edit"))
                .andExpect(flash().attribute("alert", containsString("foi alterado")));

        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(7);
    }

    @Test
    void deletesProduct() throws Exception {
        Product product = save("Caneta azul", "CN-01", "2.50", 10);

        mvc.perform(post("/products/{id}/delete", product.getId()))
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attribute("notice", "Produto excluído."));

        assertThat(products.existsById(product.getId())).isFalse();
    }

    @Test
    void missingProductIs404WithoutStackTrace() throws Exception {
        mvc.perform(get("/products/{id}/edit", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
        mvc.perform(post("/products/{id}", 999_999)
                        .param("name", "X").param("sku", "X-1").param("price", "1").param("stockQuantity", "1"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/products/{id}", 999_999).param("name", ""))
                .andExpect(status().isNotFound());
        mvc.perform(post("/products/{id}/delete", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsWithPaginationSortedByName() throws Exception {
        for (int i = 12; i >= 1; i--) {
            save("Produto %02d".formatted(i), "P-%02d".formatted(i), "1.00", 1);
        }

        mvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Produto 01")))
                .andExpect(content().string(containsString("Produto 10")))
                .andExpect(content().string(not(containsString("Produto 11"))))
                .andExpect(content().string(containsString("Página 1 de 2")));

        mvc.perform(get("/products").param("page", "1"))
                .andExpect(content().string(containsString("Produto 11")))
                .andExpect(content().string(containsString("Produto 12")))
                .andExpect(content().string(not(containsString("Produto 01"))));
    }

    @Test
    void unknownSortPropertyIsIgnoredInsteadOfFailing() throws Exception {
        save("Caneta azul", "CN-01", "2.50", 10);

        mvc.perform(get("/products").param("sort", "nope,desc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Caneta azul")));
    }

    @Test
    void searchesByNameOrSkuCaseInsensitive() throws Exception {
        save("Caneta azul", "CN-01", "2.50", 10);
        save("Lápis grafite", "LP-01", "1.00", 5);

        mvc.perform(get("/products").param("q", "CANETA"))
                .andExpect(content().string(containsString("Caneta azul")))
                .andExpect(content().string(not(containsString("Lápis grafite"))));

        mvc.perform(get("/products").param("q", "lp-"))
                .andExpect(content().string(containsString("Lápis grafite")))
                .andExpect(content().string(not(containsString("Caneta azul"))));
    }

    @Test
    void searchTreatsWildcardsLiterally() throws Exception {
        save("Caneta azul", "CN-01", "2.50", 10);

        mvc.perform(get("/products").param("q", "%"))
                .andExpect(content().string(containsString("Nenhum produto encontrado para a busca.")))
                .andExpect(content().string(not(containsString("Caneta azul"))));
    }

    @Test
    void emptyListShowsMessage() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(content().string(containsString("Nenhum produto cadastrado.")));
    }

    @Test
    void formatsPriceInReais() throws Exception {
        save("Notebook", "NB-01", "4599.90", 1);

        mvc.perform(get("/products"))
                .andExpect(content().string(containsString("R$ 4.599,90")));
    }

    @Test
    void flagsLowStockAndInactiveProducts() throws Exception {
        save("Mochila", "MC-01", "129.00", 10);
        Product inactive = save("Estojo", "ES-01", "9.90", 11);
        inactive.setActive(false);
        products.save(inactive);

        mvc.perform(get("/products"))
                .andExpect(content().string(containsString("<span class=\"stock-low\" title=\"Estoque baixo\">10</span>")))
                .andExpect(content().string(not(containsString("title=\"Estoque baixo\">11<"))))
                .andExpect(content().string(containsString("<span class=\"pill pill--off\">Inativo</span>")))
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }

    private Product save(String name, String sku, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        return products.save(product);
    }
}
