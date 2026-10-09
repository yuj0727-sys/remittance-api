package com.dbwjd.transfer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class SchemaConstraintTest {

    private static final BigDecimal VALID_SEND_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal VALID_FEE = new BigDecimal("3000");
    private static final BigDecimal VALID_TOTAL_DEBIT = new BigDecimal("13000");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sendAmountCheckRejectsAmountBelow10000() {
        assertThrows(DataIntegrityViolationException.class, () -> insert(
                "10000000-0000-0000-0000-000000000001",
                "C001",
                "key-send-amount",
                new BigDecimal("5000"),
                VALID_FEE,
                new BigDecimal("8000"),
                "REQUESTED"));
    }

    @Test
    void totalDebitCheckRejectsTotalThatIsNotSendAmountPlusFee() {
        assertThrows(DataIntegrityViolationException.class, () -> insert(
                "10000000-0000-0000-0000-000000000002",
                "C001",
                "key-total-debit",
                VALID_SEND_AMOUNT,
                VALID_FEE,
                new BigDecimal("99999"),
                "REQUESTED"));
    }

    @Test
    void statusCheckRejectsValueOutsideAllowedList() {
        assertThrows(DataIntegrityViolationException.class, () -> insert(
                "10000000-0000-0000-0000-000000000003",
                "C001",
                "key-status",
                VALID_SEND_AMOUNT,
                VALID_FEE,
                VALID_TOTAL_DEBIT,
                "WEIRD"));
    }

    @Test
    void customerForeignKeyRejectsUnknownCustomerId() {
        assertThrows(DataIntegrityViolationException.class, () -> insert(
                "10000000-0000-0000-0000-000000000004",
                "C999",
                "key-unknown-customer",
                VALID_SEND_AMOUNT,
                VALID_FEE,
                VALID_TOTAL_DEBIT,
                "REQUESTED"));
    }

    @Test
    void uniqueCustomerIdAndIdempotencyKeyRejectsSecondInsert() {
        insert("10000000-0000-0000-0000-000000000005",
                "C001",
                "key-duplicate",
                VALID_SEND_AMOUNT,
                VALID_FEE,
                VALID_TOTAL_DEBIT,
                "REQUESTED");

        assertThrows(DataIntegrityViolationException.class, () -> insert(
                "10000000-0000-0000-0000-000000000006",
                "C001",
                "key-duplicate",
                VALID_SEND_AMOUNT,
                VALID_FEE,
                VALID_TOTAL_DEBIT,
                "REQUESTED"));
    }

    @Test
    void uniqueCustomerIdAndIdempotencyKeyAllowsSameKeyForADifferentCustomer() {
        insert("10000000-0000-0000-0000-000000000007",
                "C001",
                "key-shared",
                VALID_SEND_AMOUNT,
                VALID_FEE,
                VALID_TOTAL_DEBIT,
                "REQUESTED");
        insert("10000000-0000-0000-0000-000000000008",
                "C002",
                "key-shared",
                VALID_SEND_AMOUNT,
                VALID_FEE,
                VALID_TOTAL_DEBIT,
                "REQUESTED");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE idempotency_key = ?",
                Integer.class,
                "key-shared");
        assertThat(count).isEqualTo(2);
    }

    @Test
    void customersTableContainsExactlySeedCustomersC001C002AndC003() {
        List<String> customerIds = jdbcTemplate.queryForList(
                "SELECT customer_id FROM customers ORDER BY customer_id",
                String.class);

        assertThat(customerIds).containsExactly("C001", "C002", "C003");
    }

    private void insert(String transferId,
                        String customerId,
                        String idempotencyKey,
                        BigDecimal sendAmount,
                        BigDecimal fee,
                        BigDecimal totalDebit,
                        String status) {
        jdbcTemplate.update("""
                INSERT INTO transfers (
                    transfer_id, customer_id, idempotency_key, request_hash,
                    send_currency, receive_currency,
                    send_amount, fee, total_debit, receive_amount,
                    recipient_name, status, created_at, updated_at,
                    partner_ref, failure_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                transferId,
                customerId,
                idempotencyKey,
                "a".repeat(64),
                "KRW",
                "PHP",
                sendAmount,
                fee,
                totalDebit,
                new BigDecimal("400.00"),
                "Juan",
                status,
                LocalDateTime.of(2026, 10, 9, 9, 0),
                LocalDateTime.of(2026, 10, 9, 9, 0),
                transferId);
    }
}
