package com.dbwjd.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(name = "customer_id", length = 10, nullable = false)
    private String customerId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    protected Customer() {
    }
}
