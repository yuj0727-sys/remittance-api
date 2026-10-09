package com.dbwjd.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PartnerRefTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:partnerref;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void deleteTransfers() {
        jdbcTemplate.update("DELETE FROM transfers");
    }

    @Test
    void givenPartnerRefIsReturnedUnchanged() throws Exception {
        ApiResult response = post(UUID.randomUUID().toString(), "REF-OK");

        assertEquals(201, response.status());
        assertEquals("REF-OK", response.body().get("partnerRef").asText());
        assertTrue(response.body().get("failureReason").isNull());
    }

    @Test
    void missingPartnerRefStartsWithPrAndRetryKeepsIt() throws Exception {
        String key = UUID.randomUUID().toString();

        ApiResult first = post(key, null);
        ApiResult second = post(key, null);

        assertEquals(201, first.status());
        assertEquals(201, second.status());
        assertTrue(first.body().get("partnerRef").asText().startsWith("PR-"));
        assertEquals(first.body().get("partnerRef").asText(), second.body().get("partnerRef").asText());
        assertTrue(first.body().get("failureReason").isNull());
    }

    @Test
    void samePartnerRefWithAnotherKeyReturns409() throws Exception {
        assertEquals(201, post(UUID.randomUUID().toString(), "REF-TAKEN").status());

        ApiResult conflict = post(UUID.randomUUID().toString(), "REF-TAKEN");

        assertEquals(409, conflict.status());
        assertEquals("CONFLICT", conflict.body().get("error").asText());
        assertEquals("partnerRef already used", conflict.body().get("message").asText());
    }

    @Test
    void partnerRefWithASpaceReturns400() throws Exception {
        ApiResult response = post(UUID.randomUUID().toString(), "a b");

        assertEquals(400, response.status());
        assertEquals("VALIDATION_ERROR", response.body().get("error").asText());
        assertEquals(
                "partnerRef must be 1 to 64 letters, digits, underscores, or hyphens",
                response.body().get("message").asText());
    }

    private ApiResult post(String idempotencyKey, String partnerRef) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        String partnerRefJson = partnerRef == null ? "" : ",\"partnerRef\":\"" + partnerRef + "\"";
        String json = """
                {"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan"%s}
                """.formatted(partnerRefJson);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transfers",
                new HttpEntity<>(json, headers),
                String.class);
        return new ApiResult(response.getStatusCode().value(), objectMapper.readTree(response.getBody()));
    }

    private record ApiResult(int status, JsonNode body) {
    }
}
