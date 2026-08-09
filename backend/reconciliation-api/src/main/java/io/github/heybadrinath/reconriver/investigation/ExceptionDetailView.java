package io.github.heybadrinath.reconriver.investigation;

import io.github.heybadrinath.reconriver.audit.AuditEventView;
import io.github.heybadrinath.reconriver.reconciliation.ResultView;
import java.util.List;

public record ExceptionDetailView(
        ResultView result, List<ResolutionView> resolutions, List<AuditEventView> auditEvents) {}
