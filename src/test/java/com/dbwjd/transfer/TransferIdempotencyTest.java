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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferIdempotencyTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:idempotency;DB_CLOSE_DELAY=-1");
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
    void sameKeyAndSameBodyReturnsTheOriginalTransfer() throws Exception {
        String key = UUID.randomUUID().toString();

        ApiResult first = post(key, "500000");
        ApiResult second = post(key, "500000");

        assertEquals(201, first.status());
        assertEquals(201, second.status());
        assertEquals(first.body().get("transferId").asText(), second.body().get("transferId").asText());
        assertEquals(first.body().get("createdAt").asText(), second.body().get("createdAt").asText());
        assertEquals("5000", second.body().get("fee").asText());
        assertEquals("505000", second.body().get("totalDebit").asText());
        assertEquals("20600.00", second.body().get("receiveAmount").asText());
        assertEquals(1, countTransfers());
    }

    @Test
    void sameKeyAndDifferentAmountReturns409() throws Exception {
        String key = UUID.randomUUID().toString();

        assertEquals(201, post(key, "500000").status());
        ApiResult conflict = post(key, "10000");

        assertEquals(409, conflict.status());
        assertEquals("CONFLICT", conflict.body().get("error").asText());
        assertEquals("Idempotency-Key was already used with a different request", conflict.body().get("message").asText());
        assertEquals(1, countTransfers());
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        ApiResult response = post(null, "500000");

        assertEquals(400, response.status());
        assertEquals("VALIDATION_ERROR", response.body().get("error").asText());
        assertEquals("Idempotency-Key header is required", response.body().get("message").asText());
        assertEquals(0, countTransfers());
    }

    @Test
    void tenParallelRequestsWithTheSameKeyCreateOneRow() throws Exception {
        String key = UUID.randomUUID().toString();
        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Set<Future<ApiResult>> futures = new HashSet<>();
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return post(key, "500000");
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            Set<String> transferIds = new HashSet<>();
            for (Future<ApiResult> future : futures) {
                ApiResult result = future.get(10, TimeUnit.SECONDS);
                assertEquals(201, result.status(), result.body().toString());
                transferIds.add(result.body().get("transferId").asText());
            }
            assertEquals(1, transferIds.size());
            assertEquals(1, countTransfers());
        } finally {
            executor.shutdownNow();
        }
    }

    private ApiResult post(String idempotencyKey, String sendAmount) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        String json = """
                {"customerId":"C001","sendCurrency":"KRW","sendAmount":"%s","receiveCurrency":"PHP","recipientName":"Juan"}
                """.formatted(sendAmount);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transfers",
                new HttpEntity<>(json, headers),
                String.class);
        return new ApiResult(response.getStatusCode().value(), objectMapper.readTree(response.getBody()));
    }

    private int countTransfers() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class);
        return count == null ? 0 : count;
    }

    private record ApiResult(int status, JsonNode body) {
    }
}
