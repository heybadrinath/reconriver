package io.github.heybadrinath.reconriver.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyAndFeePolicyTest {

    private final FeePolicy feePolicy =
            new FeePolicy(
                    new ReconRiverProperties(
                            null,
                            null,
                            null,
                            new ReconRiverProperties.Reconciliation("2.90", "0.30", 3)));

    @Test
    void normalizesStoredMoneyWithoutLosingFourDecimalPrecision() {
        assertThat(MoneyRules.normalize(new BigDecimal("37.38"))).isEqualByComparingTo("37.3800");
        assertThat(MoneyRules.normalize(new BigDecimal("1.23445"))).isEqualByComparingTo("1.2344");
        assertThat(MoneyRules.normalize(new BigDecimal("1.23455"))).isEqualByComparingTo("1.2346");
        assertThat(MoneyRules.equal(new BigDecimal("10.1"), new BigDecimal("10.1000"))).isTrue();
    }

    @Test
    void processorFeeUsesCurrencyScaleHalfUpThenNormalizesForStorage() {
        assertThat(feePolicy.expectedFee(new BigDecimal("1278.50")))
                .isEqualByComparingTo("37.3800");
        assertThat(feePolicy.expectedFee(new BigDecimal("100.00"))).isEqualByComparingTo("3.2000");
        assertThat(feePolicy.expectedFee(BigDecimal.ZERO)).isEqualByComparingTo("0.0000");
        assertThat(feePolicy.matches(new BigDecimal("1278.50"), new BigDecimal("37.38"))).isTrue();
        assertThat(feePolicy.matches(new BigDecimal("1278.50"), new BigDecimal("37.37"))).isFalse();
    }
}
