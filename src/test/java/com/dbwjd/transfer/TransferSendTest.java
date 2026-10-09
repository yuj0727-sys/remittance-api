package com.dbwjd.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "partner.backoff-ms=0")
class TransferSendTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:send;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakePartnerClient partnerClient;

    @BeforeEach
    void deleteTransfers() {
        jdbcTemplate.update("DELETE FROM transfers");
        partnerClient.reset();
    }

    @Test
    void okRefStaysSendingAfterOneCall() throws Exception {
        String transferId = createTransfer("OK_REF");

        ApiResult response = send(transferId);

        assertEquals(200, response.status());
        assertEquals("SENDING", response.body().get("status").asText());
        assertTrue(response.body().get("failureReason").isNull());
        assertEquals(1, partnerClient.callsFor("OK_REF"));
    }

    @Test
    void failRefBecomesFailedAfterThreeCalls() throws Exception {
        String transferId = createTransfer("FAIL_REF");

        ApiResult response = send(transferId);

        assertEquals(200, response.status());
        assertEquals("FAILED", response.body().get("status").asText());
        assertEquals("partner failed 3 times", response.body().get("failureReason").asText());
        assertEquals(3, partnerClient.callsFor("FAIL_REF"));
    }

    @Test
    void thirdAttemptAcceptedStaysSending() throws Exception {
        String transferId = createTransfer("FLAKY_REF");

        ApiResult response = send(transferId);

        assertEquals(200, response.status());
        assertEquals("SENDING", response.body().get("status").asText());
        assertTrue(response.body().get("failureReason").isNull());
        assertEquals(3, partnerClient.callsFor("FLAKY_REF"));
    }

    @Test
    void secondSendReturns409() throws Exception {
        String transferId = createTransfer("OK_REF");

        assertEquals(200, send(transferId).status());
        ApiResult again = send(transferId);

        assertEquals(409, again.status());
        assertEquals("CONFLICT", again.body().get("error").asText());
        assertEquals("cannot send transfer in status SENDING", again.body().get("message").asText());
    }

    @Test
    void cancelledSendReturns409AndMissingIdReturns404() throws Exception {
        String transferId = createTransfer("OK_REF");
        ApiResult cancelled = cancel(transferId);
        assertEquals(200, cancelled.status());

        ApiResult sendCancelled = send(transferId);
        assertEquals(409, sendCancelled.status());
        assertEquals("CONFLICT", sendCancelled.body().get("error").asText());
        assertEquals("cannot send transfer in status CANCELLED", sendCancelled.body().get("message").asText());
        assertEquals(0, partnerClient.callsFor("OK_REF"));

        ApiResult missing = send("missing-transfer");
        assertEquals(404, missing.status());
        assertEquals("NOT_FOUND", missing.body().get("error").asText());
        assertEquals("transfer not found", missing.body().get("message").asText());
    }

    @Test
    void fiveParallelSendsLetOnlyOneCallThePartner() throws Exception {
        String transferId = createTransfer("OK_REF");
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
                    return send(transferId);
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
                    assertEquals("SENDING", result.body().get("status").asText());
                } else {
                    assertEquals(409, result.status(), result.body().toString());
                    conflicts++;
                }
            }
            assertEquals(1, succeeded);
            assertEquals(4, conflicts);
            assertEquals(1, partnerClient.callsFor("OK_REF"));
        } finally {
            executor.shutdownNow();
        }
    }

    @TestConfiguration
    static class FakePartnerConfig {

        @Bean
        @Primary
        FakePartnerClient partnerClient() {
            return new FakePartnerClient();
        }
    }

    private String createTransfer(String partnerRef) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        String json = """
                {"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan","partnerRef":"%s"}
                """.formatted(partnerRef);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transfers",
                new HttpEntity<>(json, headers),
                String.class);
        assertEquals(201, response.getStatusCode().value(), response.getBody());
        return objectMapper.readTree(response.getBody()).get("transferId").asText();
    }

    private ApiResult send(String transferId) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/transfers/" + transferId + "/send",
                HttpMethod.POST,
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
