package com.dbwjd.transfer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class CreateTransferRequest {

    private String customerId;
    private String sendCurrency;
    private String sendAmount;
    private String receiveCurrency;
    private String recipientName;

    public static CreateTransferRequest of(String customerId,
                                           String sendCurrency,
                                           String sendAmount,
                                           String receiveCurrency,
                                           String recipientName) {
        CreateTransferRequest request = new CreateTransferRequest();
        request.customerId = customerId;
        request.sendCurrency = sendCurrency;
        request.sendAmount = sendAmount;
        request.receiveCurrency = receiveCurrency;
        request.recipientName = recipientName;
        return request;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getSendCurrency() {
        return sendCurrency;
    }

    public void setSendCurrency(String sendCurrency) {
        this.sendCurrency = sendCurrency;
    }

    public String getSendAmount() {
        return sendAmount;
    }

    @JsonDeserialize(using = SendAmountDeserializer.class)
    public void setSendAmount(String sendAmount) {
        this.sendAmount = sendAmount;
    }

    public String getReceiveCurrency() {
        return receiveCurrency;
    }

    public void setReceiveCurrency(String receiveCurrency) {
        this.receiveCurrency = receiveCurrency;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }
}
