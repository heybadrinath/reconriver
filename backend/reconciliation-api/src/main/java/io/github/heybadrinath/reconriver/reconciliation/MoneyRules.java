package io.github.heybadrinath.reconriver.reconciliation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyRules {
    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private MoneyRules() {}

    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING);
    }

    public static boolean equal(BigDecimal first, BigDecimal second) {
        return first != null
                && second != null
                && normalize(first).compareTo(normalize(second)) == 0;
    }
}
