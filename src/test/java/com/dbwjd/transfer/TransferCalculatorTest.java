package com.dbwjd.transfer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferCalculatorTest {

    private final TransferCalculator calculator = new TransferCalculator();

    // 500000 * 1% = 5000 -> fee 5000; 500000 + 5000 = 505000; 500000 * 0.0412 = 20600.00
    @Test
    void feeTotalDebitAndReceiveAmountFor500000() {
        TransferCalculator.Result result = calculator.calculate(new BigDecimal("500000"));

        assertEquals("5000", result.getFee().toPlainString());
        assertEquals("505000", result.getTotalDebit().toPlainString());
        assertEquals("20600.00", result.getReceiveAmount().toPlainString());
    }

    // 10000 * 1% = 100 -> max(100, 3000) = 3000
    @Test
    void minimumFeeAppliesWhenOnePercentIs100() {
        TransferCalculator.Result result = calculator.calculate(new BigDecimal("10000"));

        assertEquals("3000", result.getFee().toPlainString());
    }

    // 300000 * 1% = 3000.00 -> HALF_UP -> 3000, same as the minimum fee
    @Test
    void feeStays3000WhenOnePercentIsExactly3000() {
        TransferCalculator.Result result = calculator.calculate(new BigDecimal("300000"));

        assertEquals("3000", result.getFee().toPlainString());
    }

    // 350050 * 1% = 3500.50 -> HALF_UP -> 3501
    @Test
    void feeRoundsHalfUpFrom3500Point50To3501() {
        TransferCalculator.Result result = calculator.calculate(new BigDecimal("350050"));

        assertEquals("3501", result.getFee().toPlainString());
    }

    // 10001 * 0.0412 = 412.0412 -> HALF_UP to 2 places -> 412.04
    @Test
    void receiveAmountRoundsHalfUpToTwoDecimalPlaces() {
        TransferCalculator.Result result = calculator.calculate(new BigDecimal("10001"));

        assertEquals("412.04", result.getReceiveAmount().toPlainString());
    }
}
