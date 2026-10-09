package com.dbwjd.transfer;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record TransferResponse(
        String transferId,
        String status,
        String sendAmount,
        String fee,
        String totalDebit,
        String receiveAmount,
        String createdAt,
        String customerId,
        String sendCurrency,
        String receiveCurrency,
        String recipientName) {

    // Uses the saved row. Fee and receive amount are not calculated again.
    public static TransferResponse from(Transfer transfer) {
        String createdAt = DateTimeFormatter.ISO_INSTANT.format(transfer.getCreatedAt().toInstant(ZoneOffset.UTC));
        return new TransferResponse(
                transfer.getTransferId(),
                transfer.getStatus().name(),
                transfer.getSendAmount().toPlainString(),
                transfer.getFee().toPlainString(),
                transfer.getTotalDebit().toPlainString(),
                transfer.getReceiveAmount().toPlainString(),
                createdAt,
                transfer.getCustomerId(),
                transfer.getSendCurrency(),
                transfer.getReceiveCurrency(),
                transfer.getRecipientName());
    }
}
