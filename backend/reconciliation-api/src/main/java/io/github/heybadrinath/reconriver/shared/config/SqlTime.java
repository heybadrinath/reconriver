package io.github.heybadrinath.reconriver.shared.config;

import java.sql.Timestamp;
import java.time.Instant;

public final class SqlTime {
    private SqlTime() {}

    public static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
