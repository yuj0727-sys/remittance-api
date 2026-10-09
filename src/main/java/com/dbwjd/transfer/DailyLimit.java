package com.dbwjd.transfer;

import java.math.BigDecimal;
import java.time.ZoneId;

public final class DailyLimit {

    public static final BigDecimal MAX_SEND_AMOUNT = new BigDecimal("3000000");
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final String EXCEEDED_MESSAGE = "daily limit of 3000000 KRW exceeded";

    private DailyLimit() {
    }
}
