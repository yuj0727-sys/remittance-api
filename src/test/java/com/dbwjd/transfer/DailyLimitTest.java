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

import java.math.BigDecimal;
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
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "partner.backoff-ms=0")
class DailyLimitTest {

    private static final Instant NOON = Instant.parse("2026-10-09T03:00:00Z");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dailylimit;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SettableClock clock;

    @Autowired
    private FakePartnerClient partnerClient;

    @BeforeEach
    void reset() {
        clock.set(NOON);
        partnerClient.reset();
        jdbcTemplate.update("DELETE FROM transfers");
    }

    @Test
    void threeMillionIsAllowedAndOneWonMoreIsRejected() throws Exception {
        assertEquals(201, create("C002", "1000000").status());
        assertEquals(201, create("C002", "1000000").status());
        assertEquals(201, create("C002", "1000000").status());

        ApiResult rejected = create("C002", "10000");

        assertEquals(422, rejected.status());
        assertEquals("LIMIT_EXCEEDED", rejected.body().get("error").asText());
        assertEquals("daily limit of 3000000 KRW exceeded", rejected.body().get("message").asText());
        assertEquals(0, activeSum("C002").compareTo(new BigDecimal("3000000")));
    }

    @Test
    void cancelFreesRoomForAnotherTransfer() throws Exception {
        String firstId = create("C002", "1000000").body().get("transferId").asText();
        assertEquals(201, create("C002", "1000000").status());
        assertEquals(201, create("C002", "1000000").status());

        ApiResult cancelled = cancel(firstId);
        assertEquals(200, cancelled.status());

        assertEquals(201, create("C002", "1000000").status());
    }

    @Test
    void failedTransferIsExcludedFromTheDailySum() throws Exception {
        String failedId = create("C001", "1000000", "FAIL_REF").body().get("transferId").asText();
        assertEquals(201, create("C001", "2000000").status());
        assertEquals(422, create("C001", "10000").status());

        ApiResult sent = send(failedId);
        assertEquals(200, sent.status());
        assertEquals("FAILED", sent.body().get("status").asText());

        assertEquals(201, create("C001", "1000000").status());
    }

    @Test
    void seoulMidnightStartsANewDay() throws Exception {
        clock.set(Instant.parse("2026-10-08T14:59:00Z"));
        ApiResult filled = create("C001", "3000000");
        assertEquals(201, filled.status());
        assertEquals("2026-10-08T14:59:00Z", filled.body().get("createdAt").asText());

        clock.set(Instant.parse("2026-10-08T14:59:59Z"));
        assertEquals(422, create("C001", "10000").status());

        clock.set(Instant.parse("2026-10-08T15:00:00Z"));
        ApiResult nextDay = create("C001", "10000");
        assertEquals(201, nextDay.status());
        assertEquals("2026-10-08T15:00:00Z", nextDay.body().get("createdAt").asText());
    }

    @Test
    void retryAfterTheLimitReturnsTheOriginalTransfer() throws Exception {
        assertEquals(201, create("C002", "1000000").status());
        assertEquals(201, create("C002", "1000000").status());
        String key = UUID.randomUUID().toString();
        ApiResult last = create("C002", "1000000", null, key);
        assertEquals(201, last.status());

        ApiResult retry = create("C002", "1000000", null, key);

        assertEquals(201, retry.status());
        assertEquals(last.body().get("transferId").asText(), retry.body().get("transferId").asText());
    }

    @Test
    void tenParallelRequestsAdmitExactlySix() throws Exception {
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
                    return create("C003", "500000");
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int created = 0;
            int limited = 0;
            for (Future<?> future : futures) {
                ApiResult result = (ApiResult) future.get(15, TimeUnit.SECONDS);
                if (result.status() == 201) {
                    created++;
                } else if (result.status() == 422) {
                    limited++;
                } else {
                    fail("unexpected status " + result.status() + " " + result.body());
                }
            }
            assertEquals(6, created);
            assertEquals(4, limited);
            assertEquals(0, activeSum("C003").compareTo(new BigDecimal("3000000")));
        } finally {
            executor.shutdownNow();
        }
    }

    @TestConfiguration
    static class LimitTestConfig {

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

    private ApiResult create(String customerId, String sendAmount) throws Exception {
        return create(customerId, sendAmount, null, UUID.randomUUID().toString());
    }

    private ApiResult create(String customerId, String sendAmount, String partnerRef) throws Exception {
        return create(customerId, sendAmount, partnerRef, UUID.randomUUID().toString());
    }

    private ApiResult create(String customerId, String sendAmount, String partnerRef, String idempotencyKey)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        String partnerRefJson = partnerRef == null ? "" : ",\"partnerRef\":\"" + partnerRef + "\"";
        String json = """
                {"customerId":"%s","sendCurrency":"KRW","sendAmount":"%s","receiveCurrency":"PHP","recipientName":"Ana"%s}
                """.formatted(customerId, sendAmount, partnerRefJson);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/transfers",
                new HttpEntity<>(json, headers),
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

    private ApiResult send(String transferId) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/transfers/" + transferId + "/send",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                String.class);
        return new ApiResult(response.getStatusCode().value(), objectMapper.readTree(response.getBody()));
    }

    private BigDecimal activeSum(String customerId) {
        BigDecimal sum = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(send_amount), 0)
                FROM transfers
                WHERE customer_id = ? AND status NOT IN ('CANCELLED', 'FAILED')
                """, BigDecimal.class, customerId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private record ApiResult(int status, JsonNode body) {
    }

    static final class SettableClock extends Clock {

        private volatile Instant instant = NOON;

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
