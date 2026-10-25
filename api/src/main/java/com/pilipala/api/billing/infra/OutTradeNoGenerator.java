package com.pilipala.api.billing.infra;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class OutTradeNoGenerator {

    private static final ZoneId CN = ZoneId.of("Asia/Shanghai");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private OutTradeNoGenerator() {
    }

    public static String generate() {
        String day = LocalDate.now(CN).format(DateTimeFormatter.BASIC_ISO_DATE);
        StringBuilder sb = new StringBuilder("flr_").append(day);
        for (int i = 0; i < 10; i++) {
            sb.append(ALNUM[RANDOM.nextInt(ALNUM.length)]);
        }
        return sb.toString();
    }
}
