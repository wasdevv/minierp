package dev.wasdevv.minierp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wasdevv.minierp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Uses its own MockMvc, without the logged-in default of {@link IntegrationTest}. */
class SecurityIntegrationTest extends IntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc anonymous;

    @BeforeEach
    void setUpAnonymousClient() {
        anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void everyPageRequiresLogin() throws Exception {
        for (String path : new String[] {"/", "/products", "/customers", "/sales", "/sales/new", "/reports/sales",
                "/reports/sales.pdf?from=2026-10-01&to=2026-10-31"}) {
            anonymous.perform(get(path)).andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Test
    void stylesheetAndLoginPageArePublic() throws Exception {
        anonymous.perform(get("/css/app.css")).andExpect(status().isOk());
        anonymous.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void correctPasswordLogsInAndWrongOneDoesNot() throws Exception {
        anonymous.perform(formLogin("/login").user("admin").password(PASSWORD)).andExpect(redirectedUrl("/"));
        anonymous.perform(formLogin("/login").user("admin").password("wrong-password"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void loginErrorAndLogoutMessagesInPortuguese() throws Exception {
        anonymous.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("Usuário ou senha inválidos.")));
        anonymous.perform(get("/login").param("logout", ""))
                .andExpect(content().string(containsString("Você saiu do sistema.")));
    }

    @Test
    void postWithoutCsrfTokenIsRejectedEvenWhenLoggedIn() throws Exception {
        anonymous.perform(post("/products").with(user("admin"))
                        .param("name", "Caneta").param("sku", "CN-01").param("price", "1").param("stockQuantity", "1"))
                .andExpect(status().isForbidden());
        anonymous.perform(post("/products").with(user("admin")).with(csrf().useInvalidToken())
                        .param("name", "Caneta").param("sku", "CN-01").param("price", "1").param("stockQuantity", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void formsCarryTheCsrfToken() throws Exception {
        mvc.perform(get("/products/new")).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/sales/new")).andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void headerShowsTheUserAndALogoutForm() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(content().string(containsString("class=\"session__user\">admin</span>")))
                .andExpect(content().string(containsString("action=\"/logout\"")));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        anonymous.perform(logout("/logout")).andExpect(redirectedUrl("/login?logout"));
    }
}
