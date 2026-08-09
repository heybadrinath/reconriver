package io.github.heybadrinath.reconriver.datasetgenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MoneyPolicyTest {
    @Test
    void feePolicyUsesExactHalfUpRounding() {
        assertThat(MoneyPolicy.feeFor(new BigDecimal("100.00")))
                .isEqualByComparingTo(new BigDecimal("3.20"));
        assertThat(MoneyPolicy.feeFor(new BigDecimal("10.17")))
                .isEqualByComparingTo(new BigDecimal("0.59"));
        assertThat(MoneyPolicy.feeFor(new BigDecimal("-10.17")))
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void processorRecordRejectsAnInconsistentNetAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProcessorTransaction(
                                        "SYNTH-PROC-000001",
                                        "SYNTH-ORDER-000001",
                                        "CAPTURE",
                                        Instant.parse("2026-01-01T00:00:00Z"),
                                        new BigDecimal("100.00"),
                                        new BigDecimal("3.20"),
                                        new BigDecimal("96.81"),
                                        "USD",
                                        "SYNTH-BATCH-000001",
                                        "SETTLED"))
                .withMessageContaining("gross amount minus fee amount");
    }

    @Test
    void scenarioDefaultsProtectTheRequiredScaleProfiles() {
        assertThat(ScenarioType.CLEAN_SETTLEMENT.defaultRecordCount()).isEqualTo(100);
        assertThat(ScenarioType.MIXED_EXCEPTIONS.defaultRecordCount()).isEqualTo(1_000);
        assertThat(ScenarioType.MONTH_END_CLOSE.defaultRecordCount()).isEqualTo(10_000);
        assertThat(ScenarioType.FAILURE_RECOVERY.defaultRecordCount()).isEqualTo(10_000);
        assertThat(ScenarioType.LOCAL_PERFORMANCE.defaultRecordCount())
                .isGreaterThanOrEqualTo(100_000);
    }
}
