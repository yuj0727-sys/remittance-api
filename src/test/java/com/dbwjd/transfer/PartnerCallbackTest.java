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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "partner.backoff-ms=0")
class PartnerCallbackTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:callback;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SettableClock clock;

    @BeforeEach
    void reset() {
        clock.set(Instant.parse("2026-10-10T01:00:00Z"));
        jdbcTemplate.update("DELETE FROM callback_events");
        jdbcTemplate.update("DELETE FROM transfers");
    }

    @Test
    void callbackCompletesASendingTransfer() throws Exception {
        String transferId = sendingTransfer("OK_REF");

        ApiResult response = callback("OK_REF", "event-1");

        assertEquals(200, response.status());
        assertEquals(2, response.body().size());
        assertEquals(transferId, response.body().get("transferId").asText());
        assertEquals("COMPLETED", response.body().get("status").asText());
        assertEquals("COMPLETED", getTransfer(transferId).body().get("status").asText());
    }

    @Test
    void sameEventIdDoesNotChangeTheTransferTwice() throws Exception {
        String transferId = sendingTransfer("OK_REF");
        clock.set(Instant.parse("2026-10-10T01:00:01Z"));
        assertEquals(200, callback("OK_REF", "event-1").status());
        Timestamp updatedAt = updatedAt(transferId);

        clock.set(Instant.parse("2026-10-10T01:00:02Z"));
        ApiResult second = callback("OK_REF", "event-1");

        assertEquals(200, second.status());
        assertEquals("COMPLETED", second.body().get("status").asText());
        assertEquals(updatedAt, updatedAt(transferId));
        assertEquals(1, eventCount());
    }

    @Test
    void secondEventOnACompletedTransferReturns409() throws Exception {
        sendingTransfer("OK_REF");
        assertEquals(200, callback("OK_REF", "event-1").status());

        ApiResult second = callback("OK_REF", "event-2");

        assertEquals(409, second.status());
        assertEquals("CONFLICT", second.body().get("error").asText());
        assertEquals("cannot complete transfer in status COMPLETED", second.body().get("message").asText());
        assertEquals(1, eventCount());
    }

    @Test
    void callbackIsRejectedUnlessTheTransferIsSending() throws Exception {
        String requestedId = createTransfer("REQ-1");
        assertConflict(callback("REQ-1", "event-requested"), "REQUESTED");
        assertEquals("REQUESTED", getTransfer(requestedId).body().get("status").asText());

        String cancelledId = createTransfer("CAN-1");
        assertEquals(200, cancel(cancelledId).status());
        assertConflict(callback("CAN-1", "event-cancelled"), "CANCELLED");

        String failedId = createTransfer("FAIL_REF");
        assertEquals(200, send(failedId).status());
        assertConflict(callback("FAIL_REF", "event-failed"), "FAILED");
        assertEquals(0, eventCount());
    }

    @Test
    void unknownPartnerRefReturns404() throws Exception {
        ApiResult response = callback("MISSING", "event-1");

        assertEquals(404, response.status());
        assertEquals("NOT_FOUND", response.body().get("error").asText());
        assertEquals("transfer not found", response.body().get("message").asText());
    }

    @Test
    void tenParallelCallbacksWithTheSameEventAllReturn200() throws Exception {
        sendingTransfer("OK_REF");
        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future<?>[requestCount];
            for (int i = 0; i < requestCount; i++) {
                futures[i] = executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return callback("OK_REF", "event-race");
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<?> future : futures) {
                ApiResult result = (ApiResult) future.get(15, TimeUnit.SECONDS);
                assertEquals(200, result.status(), result.body().toString());
                assertEquals("COMPLETED", result.body().get("status").asText());
            }
            assertEquals(1, eventCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void acceptedPartnerCallStaysSendingUntilTheCallback() throws Exception {
        String transferId = createTransfer("OK_REF");
        ApiResult sent = send(transferId);

        assertEquals(200, sent.status());
        assertEquals("SENDING", sent.body().get("status").asText());

        ApiResult completed = callback("OK_REF", "event-e2e");

        assertEquals(200, completed.status());
        assertEquals("COMPLETED", completed.body().get("status").asText());
        assertEquals("COMPLETED", getTransfer(transferId).body().get("status").asText());
    }

    @Test
    void rejectsACallbackThatIsNotCompleted() throws Exception {
        ApiResult missing = postCallback("{}");
        assertEquals(400, missing.status());
        assertEquals("VALIDATION_ERROR", missing.body().get("error").asText());
        assertEquals("partnerRef is required", missing.body().get("message").asText());

        ApiResult longEventId = callback("OK_REF", "e".repeat(65));
        assertEquals(400, longEventId.status());
        assertEquals("eventId must be at most 64 characters", longEventId.body().get("message").asText());

        ApiResult wrongStatus = postCallback("""
                {"partnerRef":"OK_REF","eventId":"event-1","status":"FAILED"}
                """);
        assertEquals(400, wrongStatus.status());
        assertEquals("status must be COMPLETED", wrongStatus.body().get("message").asText());
    }

    @TestConfiguration
    static class CallbackTestConfig {

        @Bean
        @Primary
        SettableClock settableClock() {
            return new SettableClock();
        }

        @Bean
        @Primary
        FakePartnerClient partnerClient() {
            return new FakePartnerClient();
        }
    }

    private void assertConflict(ApiResult response, String status) {
        assertEquals(409, response.status(), response.body().toString());
        assertEquals("CONFLICT", response.body().get("error").asText());
        assertEquals("cannot complete transfer in status " + status, response.body().get("message").asText());
    }

    private String sendingTransfer(String partnerRef) throws Exception {
        String transferId = createTransfer(partnerRef);
        ApiResult sent = send(transferId);
        assertEquals(200, sent.status(), sent.body().toString());
        assertEquals("SENDING", sent.body().get("status").asText());
        return transferId;
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

    private ApiResult getTransfer(String transferId) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/transfers/" + transferId, String.class);
        return new ApiResult(response.getStatusCode().value(), objectMapper.readTree(response.getBody()));
    }

    private ApiResult callback(String partnerRef, String eventId) throws Exception {
        return postCallback("""
                {"partnerRef":"%s","eventId":"%s","status":"COMPLETED"}
                """.formatted(partnerRef, eventId));
    }

    private ApiResult postCallback(String json) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/callbacks/partner",
                new HttpEntity<>(json, headers),
                String.class);
        return new ApiResult(response.getStatusCode().value(), objectMapper.readTree(response.getBody()));
    }

    private Timestamp updatedAt(String transferId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM transfers WHERE transfer_id = ?",
                Timestamp.class,
                transferId);
    }

    private int eventCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM callback_events", Integer.class);
        return count == null ? 0 : count;
    }

    private record ApiResult(int status, JsonNode body) {
    }

    static final class SettableClock extends Clock {

        private volatile Instant instant = Instant.parse("2026-10-10T01:00:00Z");

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
