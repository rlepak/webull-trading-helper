package org.rl.webulltradinghelper.webull;

import java.math.BigDecimal;

public record GainerStock(String symbol, BigDecimal price, BigDecimal changeRatio) { }
