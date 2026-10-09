package com.dbwjd.transfer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferRequestValidatorTest {

    private final TransferRequestValidator validator = new TransferRequestValidator(
            customerId -> customerId.equals("C001") || customerId.equals("C002") || customerId.equals("C003"));

    @ParameterizedTest(name = "{1}")
    @MethodSource("validRequests")
    void acceptsValidRequest(CreateTransferRequest request, String expectedSendAmount) {
        assertEquals(expectedSendAmount, validator.validate(request).toPlainString());
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidRequests")
    void rejectsInvalidRequest(CreateTransferRequest request, String message) {
        ValidationException exception = assertThrows(ValidationException.class, () -> validator.validate(request));
        assertEquals(message, exception.getMessage());
    }

    static Stream<Arguments> validRequests() {
        return Stream.of(
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "10000", "PHP", "Juan"), "10000"),
                Arguments.of(CreateTransferRequest.of("C002", "KRW", "500000", "PHP", "A".repeat(100)), "500000"),
                Arguments.of(CreateTransferRequest.of("C003", "KRW", "350050", "PHP", "Maria"), "350050")
        );
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(CreateTransferRequest.of(null, "KRW", "10000", "PHP", "Juan"), "customerId is required"),
                Arguments.of(CreateTransferRequest.of(" ", "KRW", "10000", "PHP", "Juan"), "customerId is required"),
                Arguments.of(CreateTransferRequest.of("C001", null, "10000", "PHP", "Juan"), "sendCurrency is required"),
                Arguments.of(CreateTransferRequest.of("C001", " ", "10000", "PHP", "Juan"), "sendCurrency is required"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", null, "PHP", "Juan"), "sendAmount is required"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "", "PHP", "Juan"), "sendAmount is required"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", " ", "PHP", "Juan"), "sendAmount is required"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "10000", null, "Juan"), "receiveCurrency is required"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "10000", " ", "Juan"), "receiveCurrency is required"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "10000", "PHP", null), "recipientName is required"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "10000", "PHP", " "), "recipientName is required"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "abc", "PHP", "Juan"), "sendAmount must be a positive whole number"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "500000.0", "PHP", "Juan"), "sendAmount must be a positive whole number"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "1e5", "PHP", "Juan"), "sendAmount must be a positive whole number"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "-5", "PHP", "Juan"), "sendAmount must be a positive whole number"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "0", "PHP", "Juan"), "sendAmount must be a positive whole number"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", " 100 ", "PHP", "Juan"), "sendAmount must be a positive whole number"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "9999", "PHP", "Juan"), "sendAmount must be at least 10000"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "10000", "PHP", "A".repeat(101)), "recipientName must be at most 100 characters"),
                Arguments.of(CreateTransferRequest.of("C001", "USD", "10000", "PHP", "Juan"), "sendCurrency must be KRW"),
                Arguments.of(CreateTransferRequest.of("C001", "KRW", "10000", "USD", "Juan"), "receiveCurrency must be PHP"),
                Arguments.of(CreateTransferRequest.of("C999", "KRW", "10000", "PHP", "Juan"), "customerId does not exist")
        );
    }
}
