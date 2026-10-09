package com.dbwjd.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TransferCalculator {

    // String constructors keep these rates exact. No double is used.
    private static final BigDecimal FEE_RATE = new BigDecimal("0.01");
    private static final BigDecimal MINIMUM_FEE = new BigDecimal("3000");
    private static final BigDecimal EXCHANGE_RATE = new BigDecimal("0.0412");

    public Result calculate(BigDecimal sendAmount) {
        BigDecimal fee = fee(sendAmount);
        BigDecimal totalDebit = sendAmount.add(fee);
        BigDecimal receiveAmount = sendAmount.multiply(EXCHANGE_RATE).setScale(2, RoundingMode.HALF_UP);
        return new Result(fee, totalDebit, receiveAmount);
    }

    private BigDecimal fee(BigDecimal sendAmount) {
        BigDecimal onePercent = sendAmount.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP);
        return onePercent.max(MINIMUM_FEE);
    }

    public static final class Result {

        private final BigDecimal fee;
        private final BigDecimal totalDebit;
        private final BigDecimal receiveAmount;

        private Result(BigDecimal fee, BigDecimal totalDebit, BigDecimal receiveAmount) {
            this.fee = fee;
            this.totalDebit = totalDebit;
            this.receiveAmount = receiveAmount;
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
    }
}
