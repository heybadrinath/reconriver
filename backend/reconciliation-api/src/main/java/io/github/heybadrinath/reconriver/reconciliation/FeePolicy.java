package io.github.heybadrinath.reconriver.reconciliation;

import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class FeePolicy {
    private final BigDecimal rate;
    private final BigDecimal fixed;

    FeePolicy(ReconRiverProperties properties) {
        this.rate = new BigDecimal(properties.reconciliation().feePercent()).movePointLeft(2);
        this.fixed = new BigDecimal(properties.reconciliation().feeFixed());
    }

    public BigDecimal expectedFee(BigDecimal gross) {
        if (gross.signum() <= 0) {
            return BigDecimal.ZERO.setScale(MoneyRules.SCALE);
        }
        return gross.multiply(rate)
                .add(fixed)
                .setScale(2, RoundingMode.HALF_UP)
                .setScale(MoneyRules.SCALE);
    }

    public boolean matches(BigDecimal gross, BigDecimal actualFee) {
        return MoneyRules.equal(expectedFee(gross), actualFee);
    }
}
