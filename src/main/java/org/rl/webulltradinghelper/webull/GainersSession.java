package org.rl.webulltradinghelper.webull;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

public enum GainersSession {
    PRE_MARKET, DAY_1, AFTER_MARKET;

    public static GainersSession at(Instant instant, ZoneId zone) {
        LocalTime time = instant.atZone(zone).toLocalTime();
        if (time.isBefore(LocalTime.of(3, 0)) || !time.isBefore(LocalTime.of(15, 0))) return AFTER_MARKET;
        return time.isBefore(LocalTime.of(8, 30)) ? PRE_MARKET : DAY_1;
    }
}
