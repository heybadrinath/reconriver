package io.github.heybadrinath.reconriver.reconciliation;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ControlledFailureService {
    private final JdbcClient jdbc;

    ControlledFailureService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean triggerOnce(UUID runId) {
        return jdbc.sql(
                                "UPDATE reconciliation_run SET controlled_failure_triggered=true WHERE id=:id AND controlled_failure_triggered=false")
                        .param("id", runId)
                        .update()
                == 1;
    }
}
