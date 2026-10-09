package com.dbwjd.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @Column(name = "transfer_id", length = 36, nullable = false)
    private String transferId;

    @Column(name = "customer_id", length = 10, nullable = false)
    private String customerId;

    @Column(name = "idempotency_key", length = 64, nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "send_currency", length = 3, nullable = false)
    private String sendCurrency;

    @Column(name = "receive_currency", length = 3, nullable = false)
    private String receiveCurrency;

    // KRW amounts are BIGINT in the database. PHP is DECIMAL(19, 2). Both are BigDecimal here.
    @Column(name = "send_amount", nullable = false)
    private BigDecimal sendAmount;

    @Column(name = "fee", nullable = false)
    private BigDecimal fee;

    @Column(name = "total_debit", nullable = false)
    private BigDecimal totalDebit;

    @Column(name = "receive_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal receiveAmount;

    @Column(name = "recipient_name", length = 100, nullable = false)
    private String recipientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TransferStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "partner_ref", length = 64, nullable = false)
    private String partnerRef;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    // Required by JPA.
    protected Transfer() {
    }

    public Transfer(String transferId,
                    String customerId,
                    String idempotencyKey,
                    String requestHash,
                    String sendCurrency,
                    String receiveCurrency,
                    BigDecimal sendAmount,
                    BigDecimal fee,
                    BigDecimal totalDebit,
                    BigDecimal receiveAmount,
                    String recipientName,
                    TransferStatus status,
                    LocalDateTime createdAt,
                    LocalDateTime updatedAt,
                    String partnerRef,
                    String failureReason) {
        this.transferId = transferId;
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.sendCurrency = sendCurrency;
        this.receiveCurrency = receiveCurrency;
        this.sendAmount = sendAmount;
        this.fee = fee;
        this.totalDebit = totalDebit;
        this.receiveAmount = receiveAmount;
        this.recipientName = recipientName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.partnerRef = partnerRef;
        this.failureReason = failureReason;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getSendCurrency() {
        return sendCurrency;
    }

    public String getReceiveCurrency() {
        return receiveCurrency;
    }

    public BigDecimal getSendAmount() {
        return sendAmount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public BigDecimal getReceiveAmount() {
        return receiveAmount;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getPartnerRef() {
        return partnerRef;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
