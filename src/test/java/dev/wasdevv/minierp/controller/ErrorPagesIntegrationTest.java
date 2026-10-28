package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wasdevv.minierp.support.IntegrationTest;
import dev.wasdevv.minierp.support.RequiresDatabase;
import dev.wasdevv.minierp.support.TestDatabase;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Error pages are rendered by the servlet container's error dispatch, which MockMvc skips; this test runs
 * a real server to check what a browser actually receives.
 */
@ExtendWith(RequiresDatabase.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "minierp.admin.password=" + IntegrationTest.PASSWORD)
class ErrorPagesIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    private TestRestTemplate http;

    /** Redirects are the subject here (login, session cookies), so they are never followed. */
    @Autowired
    void client(TestRestTemplate template) {
        this.http = template.withRedirects(ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW);
    }

    @Test
    void unknownPageIsA404PageInPortuguese() {
        ResponseEntity<String> response = browserGet("/does-not-exist");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Não encontrado").doesNotContain("Whitelabel");
    }

    @Test
    void malformedIdIsAnErrorPageWithoutStackTrace() {
        ResponseEntity<String> response = browserGet("/products/abc/edit");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Algo deu errado")
                .doesNotContain("Exception", "at dev.wasdevv", "NumberFormat", "Whitelabel");
    }

    @Test
    void anonymousVisitorIsSentToLogin() {
        ResponseEntity<String> response = http.exchange("/products", HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasPath("/login");
    }

    /** Logged in the way a browser does it: login page, CSRF token, form post, session cookie. */
    private ResponseEntity<String> browserGet(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        headers.add(HttpHeaders.COOKIE, login());
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String login() {
        ResponseEntity<String> page = http.getForEntity("/login", String.class);
        Matcher token = CSRF.matcher(page.getBody());
        assertThat(token.find()).as("login page has a CSRF token").isTrue();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, sessionCookie(page));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "admin");
        form.add("password", IntegrationTest.PASSWORD);
        form.add("_csrf", token.group(1));
        ResponseEntity<String> loggedIn = http.postForEntity("/login", new HttpEntity<>(form, headers), String.class);
        assertThat(loggedIn.getHeaders().getLocation()).as("login succeeded").hasPath("/");
        return sessionCookie(loggedIn);
    }

    private static String sessionCookie(ResponseEntity<String> response) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).getFirst().split(";", 2)[0];
    }
}
