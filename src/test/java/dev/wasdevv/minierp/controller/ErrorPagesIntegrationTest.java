package dev.wasdevv.minierp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wasdevv.minierp.support.RequiresDatabase;
import dev.wasdevv.minierp.support.TestDatabase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Error pages are rendered by the servlet container's error dispatch, which MockMvc skips; this test runs
 * a real server to check what a browser actually receives.
 */
@ExtendWith(RequiresDatabase.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPagesIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    private TestRestTemplate http;

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

    private ResponseEntity<String> browserGet(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
