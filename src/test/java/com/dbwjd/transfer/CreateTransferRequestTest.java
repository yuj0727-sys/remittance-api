package com.dbwjd.transfer;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTransferRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsSendAmountFromJsonString() throws Exception {
        CreateTransferRequest request = objectMapper.readValue(json("\"10000\""), CreateTransferRequest.class);

        assertEquals("10000", request.getSendAmount());
    }

    @Test
    void readsSendAmountFromJsonInteger() throws Exception {
        CreateTransferRequest request = objectMapper.readValue(json("10000"), CreateTransferRequest.class);

        assertEquals("10000", request.getSendAmount());
    }

    @Test
    void rejectsJsonDecimalSendAmount() {
        JsonMappingException exception = assertThrows(JsonMappingException.class,
                () -> objectMapper.readValue(json("1.5"), CreateTransferRequest.class));

        assertInstanceOf(ValidationException.class, exception.getCause());
        assertEquals("sendAmount must be a whole number", exception.getCause().getMessage());
    }

    private String json(String sendAmount) {
        return """
                {
                  "customerId": "C001",
                  "sendCurrency": "KRW",
                  "sendAmount": %s,
                  "receiveCurrency": "PHP",
                  "recipientName": "Juan"
                }
                """.formatted(sendAmount);
    }
}
