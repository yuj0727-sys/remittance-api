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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferCancelTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:cancel;DB_CLOSE_DELAY=-1");
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
    void cancelOnceReturns200AndSecondCancelReturns409() throws Exception {
        String transferId = createTransfer();

        ApiResult loaded = get(transferId);
        assertEquals(200, loaded.status());
        assertEquals(transferId, loaded.body().get("transferId").asText());
        assertEquals("REQUESTED", loaded.body().get("status").asText());
        assertEquals("500000", loaded.body().get("sendAmount").asText());
        assertEquals("5000", loaded.body().get("fee").asText());
        assertEquals("20600.00", loaded.body().get("receiveAmount").asText());

        ApiResult cancelled = cancel(transferId);
        assertEquals(200, cancelled.status());
        assertEquals(transferId, cancelled.body().get("transferId").asText());
        assertEquals("CANCELLED", cancelled.body().get("status").asText());
        assertEquals("500000", cancelled.body().get("sendAmount").asText());
        assertEquals("5000", cancelled.body().get("fee").asText());
        assertEquals("505000", cancelled.body().get("totalDebit").asText());
        assertEquals("20600.00", cancelled.body().get("receiveAmount").asText());

        ApiResult again = cancel(transferId);
        assertEquals(409, again.status());
        assertEquals("CONFLICT", again.body().get("error").asText());
        assertEquals("cannot cancel transfer in status CANCELLED", again.body().get("message").asText());
    }

    @Test
    void cancelMissingTransferReturns404() throws Exception {
        ApiResult response = cancel("missing-transfer");

        assertEquals(404, response.status());
        assertEquals("NOT_FOUND", response.body().get("error").asText());
        assertEquals("transfer not found", response.body().get("message").asText());
    }

    @Test
    void getMissingTransferReturns404() throws Exception {
        ApiResult response = get("missing-transfer");

        assertEquals(404, response.status());
        assertEquals("NOT_FOUND", response.body().get("error").asText());
        assertEquals("transfer not found", response.body().get("message").asText());
    }

    @Test
    void fiveParallelCancelsLetOnlyOneSucceed() throws Exception {
        String transferId = createTransfer();
        int requestCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future<?>[requestCount];
            for (int i = 0; i < requestCount; i++) {
                futures[i] = executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return cancel(transferId);
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int succeeded = 0;
            int conflicts = 0;
            for (Future<?> future : futures) {
                ApiResult result = (ApiResult) future.get(10, TimeUnit.SECONDS);
                if (result.status() == 200) {
                    succeeded++;
                    assertEquals("CANCELLED", result.body().get("status").asText());
                } else {
                    assertEquals(409, result.status(), result.body().toString());
                    conflicts++;
                }
            }
            assertEquals(1, succeeded);
            assertEquals(4, conflicts);
        } finally {
            executor.shutdownNow();
        }
    }

    private String createTransfer() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        String json = """
                {"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan"}
                """;
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transfers",
                new HttpEntity<>(json, headers),
                String.class);
        assertEquals(201, response.getStatusCode().value(), response.getBody());
        return objectMapper.readTree(response.getBody()).get("transferId").asText();
    }

    private ApiResult get(String transferId) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/transfers/" + transferId,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);
        return new ApiResult(response.getStatusCode().value(), objectMapper.readTree(response.getBody()));
    }

    private ApiResult cancel(String transferId) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/transfers/" + transferId + "/cancel",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                String.class);
        return new ApiResult(response.getStatusCode().value(), objectMapper.readTree(response.getBody()));
    }

    private record ApiResult(int status, JsonNode body) {
    }
}
