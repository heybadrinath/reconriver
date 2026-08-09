package io.github.heybadrinath.reconriver.datasetgenerator;

import java.util.Arrays;

public enum ScenarioType {
    CLEAN_SETTLEMENT("clean-settlement", "Clean settlement", 100),
    MIXED_EXCEPTIONS("mixed-exceptions", "Mixed exceptions", 1_000),
    MONTH_END_CLOSE("month-end-close", "Month-end close", 10_000),
    FAILURE_RECOVERY("failure-recovery", "Failure recovery", 10_000),
    LOCAL_PERFORMANCE("local-performance", "Local performance", 100_000);

    private final String slug;
    private final String displayName;
    private final int defaultRecordCount;

    ScenarioType(String slug, String displayName, int defaultRecordCount) {
        this.slug = slug;
        this.displayName = displayName;
        this.defaultRecordCount = defaultRecordCount;
    }

    public String slug() {
        return slug;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultRecordCount() {
        return defaultRecordCount;
    }

    public static ScenarioType fromSlug(String value) {
        return Arrays.stream(values())
                .filter(type -> type.slug.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unknown scenario '%s'. Expected one of: clean-settlement, mixed-exceptions, "
                                                        .formatted(value)
                                                + "month-end-close, failure-recovery, local-performance"));
    }
}
