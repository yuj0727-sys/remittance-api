package com.dbwjd.transfer;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class TransferRequestValidator {

    private static final BigDecimal MINIMUM_SEND_AMOUNT = new BigDecimal("10000");
    // Digits only. Rejects a sign, decimal point, exponent, space, or leading zero.
    private static final Pattern POSITIVE_WHOLE_NUMBER = Pattern.compile("[1-9][0-9]*");
    private static final int MAX_RECIPIENT_NAME_LENGTH = 100;
    private static final Pattern PARTNER_REF = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final CustomerLookup customerLookup;

    public TransferRequestValidator(CustomerLookup customerLookup) {
        this.customerLookup = customerLookup;
    }

    public BigDecimal validate(CreateTransferRequest request) {
        requireText(request.getCustomerId(), "customerId");
        requireText(request.getSendCurrency(), "sendCurrency");
        requireText(request.getSendAmount(), "sendAmount");
        requireText(request.getReceiveCurrency(), "receiveCurrency");
        requireText(request.getRecipientName(), "recipientName");

        if (!POSITIVE_WHOLE_NUMBER.matcher(request.getSendAmount()).matches()) {
            throw new ValidationException("sendAmount must be a positive whole number");
        }

        BigDecimal sendAmount = new BigDecimal(request.getSendAmount());
        if (sendAmount.compareTo(MINIMUM_SEND_AMOUNT) < 0) {
            throw new ValidationException("sendAmount must be at least 10000");
        }

        if (request.getRecipientName().length() > MAX_RECIPIENT_NAME_LENGTH) {
            throw new ValidationException("recipientName must be at most 100 characters");
        }

        if (!request.getSendCurrency().equals("KRW")) {
            throw new ValidationException("sendCurrency must be KRW");
        }
        if (!request.getReceiveCurrency().equals("PHP")) {
            throw new ValidationException("receiveCurrency must be PHP");
        }

        if (request.getPartnerRef() != null && !PARTNER_REF.matcher(request.getPartnerRef()).matches()) {
            throw new ValidationException("partnerRef must be 1 to 64 letters, digits, underscores, or hyphens");
        }

        if (!customerLookup.exists(request.getCustomerId())) {
            throw new ValidationException("customerId does not exist");
        }

        return sendAmount;
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }
    }
}
