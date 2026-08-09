package io.github.heybadrinath.reconriver.datasetgenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class MoneyPolicy {
    static final BigDecimal PERCENTAGE_RATE = new BigDecimal("0.0290");
    static final BigDecimal FIXED_CHARGE = new BigDecimal("0.30");
    static final int SCALE = 2;

    private MoneyPolicy() {}

    static BigDecimal money(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, SCALE);
    }

    static BigDecimal feeFor(BigDecimal grossAmount) {
        if (grossAmount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return grossAmount
                .multiply(PERCENTAGE_RATE)
                .add(FIXED_CHARGE)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal scaled(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
