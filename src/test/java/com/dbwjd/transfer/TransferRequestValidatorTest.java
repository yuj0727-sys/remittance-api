package com.dbwjd.transfer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferRequestValidatorTest {

    private static final String NO_ERROR = "no error";

    private final TransferRequestValidator validator = new TransferRequestValidator(
            customerId -> "C001".equals(customerId));

    // Each row is: input -> expected error message. "no error" means the input is valid.
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("cases")
    void validates(String input, String expectedMessage, CreateTransferRequest request) {
        if (NO_ERROR.equals(expectedMessage)) {
            assertEquals(request.getSendAmount(), validator.validate(request).toPlainString(), input);
            return;
        }

        ValidationException exception = assertThrows(ValidationException.class, () -> validator.validate(request));
        assertEquals(expectedMessage, exception.getMessage(), input);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                line("missing customerId", null, "KRW", "10000", "PHP", "Juan", "customerId is required"),
                line("missing sendCurrency", "C001", null, "10000", "PHP", "Juan", "sendCurrency is required"),
                line("missing sendAmount", "C001", "KRW", null, "PHP", "Juan", "sendAmount is required"),
                line("missing receiveCurrency", "C001", "KRW", "10000", null, "Juan", "receiveCurrency is required"),
                line("missing recipientName", "C001", "KRW", "10000", "PHP", null, "recipientName is required"),
                line("sendAmount \"abc\"", "C001", "KRW", "abc", "PHP", "Juan", "sendAmount must be a positive whole number"),
                line("sendAmount \"\"", "C001", "KRW", "", "PHP", "Juan", "sendAmount is required"),
                line("sendAmount \"500000.0\"", "C001", "KRW", "500000.0", "PHP", "Juan", "sendAmount must be a positive whole number"),
                line("sendAmount \"1e5\"", "C001", "KRW", "1e5", "PHP", "Juan", "sendAmount must be a positive whole number"),
                line("sendAmount \"-5\"", "C001", "KRW", "-5", "PHP", "Juan", "sendAmount must be a positive whole number"),
                line("sendAmount \"0\"", "C001", "KRW", "0", "PHP", "Juan", "sendAmount must be a positive whole number"),
                line("sendAmount \" 100 \"", "C001", "KRW", " 100 ", "PHP", "Juan", "sendAmount must be a positive whole number"),
                line("sendAmount \"9999\"", "C001", "KRW", "9999", "PHP", "Juan", "sendAmount must be at least 10000"),
                line("sendAmount \"10000\"", "C001", "KRW", "10000", "PHP", "Juan", null),
                line("recipientName 100 chars", "C001", "KRW", "10000", "PHP", "A".repeat(100), null),
                line("recipientName 101 chars", "C001", "KRW", "10000", "PHP", "A".repeat(101), "recipientName must be at most 100 characters"),
                line("sendCurrency \"USD\"", "C001", "USD", "10000", "PHP", "Juan", "sendCurrency must be KRW"),
                line("receiveCurrency \"KRW\"", "C001", "KRW", "10000", "KRW", "Juan", "receiveCurrency must be PHP"),
                line("customerId \"C999\"", "C999", "KRW", "10000", "PHP", "Juan", "customerId does not exist")
        );
    }

    private static Arguments line(String input,
                                  String customerId,
                                  String sendCurrency,
                                  String sendAmount,
                                  String receiveCurrency,
                                  String recipientName,
                                  String expectedMessage) {
        return Arguments.of(
                input,
                expectedMessage == null ? NO_ERROR : expectedMessage,
                CreateTransferRequest.of(customerId, sendCurrency, sendAmount, receiveCurrency, recipientName));
    }
}
