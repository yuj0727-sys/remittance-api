package com.finshot.transfer;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransferApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void applicationStartsAndLoadsSchema() {
        Integer customerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers", Integer.class);
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class);

        assertThat(customerCount).isEqualTo(3);
    }

    @Test
    @Transactional
    void savesAndReadsATransfer() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 9, 0);
        Transfer transfer = new Transfer(
                "11111111-1111-1111-1111-111111111111",
                "C001",
                "idem-key-1",
                "a".repeat(64),
                "KRW",
                "PHP",
                new BigDecimal("10000"),
                new BigDecimal("3000"),
                new BigDecimal("13000"),
                new BigDecimal("400.00"),
                "Juan",
                TransferStatus.REQUESTED,
                now,
                now
        );

        transferRepository.save(transfer);
        entityManager.flush();
        entityManager.clear();

        Transfer found = transferRepository.findById(transfer.getTransferId()).orElseThrow();

        assertThat(found.getCustomerId()).isEqualTo("C001");
        assertThat(found.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(found.getSendAmount()).isEqualByComparingTo("10000");
        assertThat(found.getReceiveAmount()).isEqualByComparingTo("400.00");
    }
}
