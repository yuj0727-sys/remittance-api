package com.dbwjd.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsValidationExceptionTo400() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleValidation(
                new ValidationException("sendAmount must be at least 10000"));

        assertBody(response, 400, "VALIDATION_ERROR", "sendAmount must be at least 10000");
    }

    @Test
    void mapsNotFoundExceptionTo404() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new NotFoundException("transfer not found"));

        assertBody(response, 404, "NOT_FOUND", "transfer not found");
    }

    @Test
    void mapsConflictExceptionTo409() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleConflict(new ConflictException("duplicate key"));

        assertBody(response, 409, "CONFLICT", "duplicate key");
    }

    @Test
    void mapsLimitExceededExceptionTo422() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleLimit(
                new LimitExceededException("daily limit of 3000000 KRW exceeded"));

        assertBody(response, 422, "LIMIT_EXCEEDED", "daily limit of 3000000 KRW exceeded");
    }

    @Test
    void mapsBrokenJsonTo400() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleBadJson(
                new HttpMessageNotReadableException("bad json"));

        assertBody(response, 400, "VALIDATION_ERROR", "request body is invalid");
    }

    @Test
    void mapsSendAmountParseFailureTo400() throws Exception {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "bad amount",
                new ValidationException("sendAmount must be a whole number"));

        ResponseEntity<ErrorResponse> response = handler.handleBadJson(exception);

        assertBody(response, 400, "VALIDATION_ERROR", "sendAmount must be a whole number");
    }

    @Test
    void mapsUnexpectedExceptionTo500WithoutDetails() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new IllegalStateException("secret"));

        assertBody(response, 500, "INTERNAL_ERROR", "unexpected error");
        assertFalse(objectMapper.writeValueAsString(response.getBody()).contains("secret"));
    }

    private void assertBody(ResponseEntity<ErrorResponse> response, int status, String code, String message)
            throws Exception {
        assertEquals(status, response.getStatusCode().value());

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response.getBody()));
        assertEquals(2, json.size());
        assertEquals(code, json.get("error").asText());
        assertEquals(message, json.get("message").asText());
    }
}
