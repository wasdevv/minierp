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

import dev.wasdevv.minierp.model.Customer;
import dev.wasdevv.minierp.repository.CustomerRepository;
import dev.wasdevv.minierp.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Fictitious customers only; the CPFs/CNPJs are the usual check-digit test numbers. */
class CustomerControllerIntegrationTest extends IntegrationTest {

    private static final String CPF = "52998224725";
    private static final String OTHER_CPF = "11144477735";
    private static final String CNPJ = "11222333000181";

    @Autowired
    private CustomerRepository customers;

    @Test
    void createsCustomerAndNormalizesInput() throws Exception {
        mvc.perform(post("/customers")
                        .param("name", "  Maria Souza ")
                        .param("email", " Maria@Exemplo.COM ")
                        .param("document", "529.982.247-25"))
                .andExpect(redirectedUrl("/customers"))
                .andExpect(flash().attribute("notice", "Cliente cadastrado."));

        Customer saved = customers.findAll().getFirst();
        assertThat(saved.getName()).isEqualTo("Maria Souza");
        assertThat(saved.getEmail()).isEqualTo("maria@exemplo.com");
        assertThat(saved.getDocument()).isEqualTo(CPF);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void createsCustomerWithOnlyName() throws Exception {
        mvc.perform(post("/customers").param("name", "Balcão").param("email", "").param("document", ""))
                .andExpect(redirectedUrl("/customers"));

        Customer saved = customers.findAll().getFirst();
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getDocument()).isNull();
    }

    @Test
    void severalCustomersWithoutDocumentAreAllowed() throws Exception {
        save("Cliente A", null, null);

        mvc.perform(post("/customers").param("name", "Cliente B"))
                .andExpect(redirectedUrl("/customers"));

        assertThat(customers.count()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidCustomerWithFieldMessages() throws Exception {
        mvc.perform(post("/customers")
                        .param("name", "")
                        .param("email", "maria@")
                        .param("document", "529.982.247-24"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers/form"))
                .andExpect(model().attributeHasFieldErrors("customer", "name", "email", "document"))
                .andExpect(content().string(containsString("Informe um e-mail válido.")))
                .andExpect(content().string(containsString("Informe um CPF ou CNPJ válido.")));

        assertThat(customers.count()).isZero();
    }

    @Test
    void rejectsDuplicateDocumentWhateverTheFormatting() throws Exception {
        save("Maria Souza", null, CPF);

        mvc.perform(post("/customers").param("name", "Outra Maria").param("document", "52998224725"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("customer", "document", "duplicate"))
                .andExpect(content().string(containsString("Já existe um cliente com este CPF/CNPJ.")));

        assertThat(customers.count()).isEqualTo(1);
    }

    @Test
    void databaseRejectsMalformedDocumentEvenBypassingValidation() {
        Customer customer = new Customer();
        customer.setName("Direto no banco");
        customer.setDocument("123");

        assertThatThrownBy(() -> customers.saveAndFlush(customer))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void editFormShowsFormattedDocument() throws Exception {
        Customer customer = save("Empresa Exemplo Ltda", "contato@exemplo.com", CNPJ);

        mvc.perform(get("/customers/{id}/edit", customer.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Empresa Exemplo Ltda\"")))
                .andExpect(content().string(containsString("value=\"11.222.333/0001-81\"")));
    }

    @Test
    void updatesCustomerKeepingOwnDocument() throws Exception {
        Customer customer = save("Maria Souza", "maria@exemplo.com", CPF);

        mvc.perform(post("/customers/{id}", customer.getId())
                        .param("name", "Maria S. Lima")
                        .param("email", "")
                        .param("document", "529.982.247-25"))
                .andExpect(redirectedUrl("/customers"))
                .andExpect(flash().attribute("notice", "Cliente atualizado."));

        Customer updated = customers.findById(customer.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Maria S. Lima");
        assertThat(updated.getEmail()).isNull();
        assertThat(updated.getDocument()).isEqualTo(CPF);
    }

    @Test
    void updateRejectsDocumentOfAnotherCustomer() throws Exception {
        save("Maria Souza", null, CPF);
        Customer other = save("João Lima", null, OTHER_CPF);

        mvc.perform(post("/customers/{id}", other.getId()).param("name", "João Lima").param("document", CPF))
                .andExpect(model().attributeHasFieldErrorCode("customer", "document", "duplicate"));

        assertThat(customers.findById(other.getId()).orElseThrow().getDocument()).isEqualTo(OTHER_CPF);
    }

    @Test
    void deletesCustomer() throws Exception {
        Customer customer = save("Maria Souza", null, null);

        mvc.perform(post("/customers/{id}/delete", customer.getId()))
                .andExpect(redirectedUrl("/customers"))
                .andExpect(flash().attribute("notice", "Cliente excluído."));

        assertThat(customers.existsById(customer.getId())).isFalse();
    }

    @Test
    void missingCustomerIs404() throws Exception {
        mvc.perform(get("/customers/{id}/edit", 999_999)).andExpect(status().isNotFound());
        mvc.perform(post("/customers/{id}", 999_999).param("name", "X")).andExpect(status().isNotFound());
        mvc.perform(post("/customers/{id}", 999_999).param("name", "")).andExpect(status().isNotFound());
        mvc.perform(post("/customers/{id}/delete", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void listMasksDocumentsAndNeverShowsThemInFull() throws Exception {
        save("Maria Souza", "maria@exemplo.com", CPF);
        save("Empresa Exemplo Ltda", null, CNPJ);
        save("Balcão", null, null);

        mvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("***.982.247-**")))
                .andExpect(content().string(containsString("**.222.333/0001-**")))
                .andExpect(content().string(not(containsString("529.982.247-25"))))
                .andExpect(content().string(not(containsString(CPF))))
                .andExpect(content().string(containsString("aria-current=\"page\">Clientes")))
                .andExpect(content().string(containsString("3 cliente(s)")));
    }

    @Test
    void searchesByNameOrEmailCaseInsensitive() throws Exception {
        save("Maria Souza", "maria@exemplo.com", null);
        save("João Lima", "joao@empresa.com", null);

        mvc.perform(get("/customers").param("q", "SOUZA"))
                .andExpect(content().string(containsString("Maria Souza")))
                .andExpect(content().string(not(containsString("João Lima"))));

        mvc.perform(get("/customers").param("q", "@empresa"))
                .andExpect(content().string(containsString("João Lima")))
                .andExpect(content().string(not(containsString("Maria Souza"))));
    }

    @Test
    void searchesByFullDocumentButNotByFragment() throws Exception {
        save("Maria Souza", null, CPF);
        save("João Lima", null, OTHER_CPF);

        mvc.perform(get("/customers").param("q", "529.982.247-25"))
                .andExpect(content().string(containsString("Maria Souza")))
                .andExpect(content().string(not(containsString("João Lima"))));

        mvc.perform(get("/customers").param("q", "52998"))
                .andExpect(content().string(containsString("Nenhum cliente encontrado para a busca.")));
    }

    @Test
    void listsWithPagination() throws Exception {
        for (int i = 12; i >= 1; i--) {
            save("Cliente %02d".formatted(i), null, null);
        }

        mvc.perform(get("/customers"))
                .andExpect(content().string(containsString("Cliente 10")))
                .andExpect(content().string(not(containsString("Cliente 11"))))
                .andExpect(content().string(containsString("Página 1 de 2")));

        mvc.perform(get("/customers").param("page", "1").param("sort", "nope"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cliente 12")));
    }

    @Test
    void emptyListShowsMessage() throws Exception {
        mvc.perform(get("/customers"))
                .andExpect(content().string(containsString("Nenhum cliente cadastrado.")));
    }

    private Customer save(String name, String email, String document) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(email);
        customer.setDocument(document);
        return customers.save(customer);
    }
}
