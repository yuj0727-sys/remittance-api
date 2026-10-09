package com.dbwjd.transfer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCustomerLookup implements CustomerLookup {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCustomerLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean exists(String customerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE customer_id = ?",
                Integer.class,
                customerId);
        return count != null && count > 0;
    }
}
