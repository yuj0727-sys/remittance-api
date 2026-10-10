package com.dbwjd.transfer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "partner.backoff-ms=0")
class CallbackRaceTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:callbackrace;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RacePartnerClient partnerClient;

    @BeforeEach
    void reset() {
        partnerClient.reset();
        jdbcTemplate.update("DELETE FROM callback_events");
        jdbcTemplate.update("DELETE FROM transfers");
    }

    @Test
    void callbackDuringAcceptLeavesTheTransferCompleted() throws Exception {
        String transferId = createTransfer("RACE_OK");

        ApiResult sent = send(transferId);

        assertEquals(200, sent.status(), sent.body().toString());
        assertEquals("COMPLETED", sent.body().get("status").asText());
        assertTrue(sent.body().get("failureReason").isNull());
        assertEquals("COMPLETED", getTransfer(transferId).body().get("status").asText());
        assertEquals(1, partnerClient.calls());
    }

    @Test
    void callbackBeforeTheLastFailureIsNotOverwritten() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(TransferStateService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            String transferId = createTransfer("RACE_FAIL");

            ApiResult sent = send(transferId);

            assertEquals(200, sent.status(), sent.body().toString());
            assertEquals("COMPLETED", sent.body().get("status").asText());
            assertTrue(sent.body().get("failureReason").isNull());
            assertEquals(3, partnerClient.calls());
            assertTrue(logs.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("partner failure not saved")
                            && event.getFormattedMessage().contains("COMPLETED")));
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void callbackBeforeSendingReturns409() throws Exception {
        String transferId = createTransfer("STILL_REQUESTED");

        ApiResult callback = callback("STILL_REQUESTED", "event-early");

        assertEquals(409, callback.status(), callback.body().toString());
        assertEquals("CONFLICT", callback.body().get("error").asText());
        assertEquals("cannot complete transfer in status REQUESTED", callback.body().get("message").asText());
        assertEquals("REQUESTED", getTransfer(transferId).body().get("status").asText());
        assertEquals(0, eventCount());
    }

    @TestConfiguration
    static class RaceConfig {

        @Bean
        @Primary
        RacePartnerClient racePartnerClient(PartnerCallbackService callbacks) {
            return new RacePartnerClient(callbacks);
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

    private ApiResult getTransfer(String transferId) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/transfers/" + transferId, String.class);
        return new ApiResult(response.getStatusCode().value(), objectMapper.readTree(response.getBody()));
    }

    private ApiResult callback(String partnerRef, String eventId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = """
                {"partnerRef":"%s","eventId":"%s","status":"COMPLETED"}
                """.formatted(partnerRef, eventId);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/callbacks/partner",
                new HttpEntity<>(json, headers),
                String.class);
        return new ApiResult(response.getStatusCode().value(), objectMapper.readTree(response.getBody()));
    }

    private int eventCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM callback_events", Integer.class);
        return count == null ? 0 : count;
    }

    private record ApiResult(int status, JsonNode body) {
    }

    static final class RacePartnerClient implements PartnerClient {

        private final PartnerCallbackService callbacks;
        private final AtomicInteger calls = new AtomicInteger();

        RacePartnerClient(PartnerCallbackService callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public PartnerResult call(String partnerRef) {
            int attempt = calls.incrementAndGet();
            if ("RACE_OK".equals(partnerRef)) {
                receive(partnerRef, "event-accept");
                return PartnerResult.accepted();
            }
            if ("RACE_FAIL".equals(partnerRef)) {
                if (attempt == 2) {
                    receive(partnerRef, "event-fail");
                }
                return PartnerResult.failed("rejected");
            }
            throw new IllegalStateException("unexpected partnerRef " + partnerRef);
        }

        private void receive(String partnerRef, String eventId) {
            PartnerCallbackRequest request = new PartnerCallbackRequest();
            request.setPartnerRef(partnerRef);
            request.setEventId(eventId);
            request.setStatus("COMPLETED");
            callbacks.receive(request);
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }
}
