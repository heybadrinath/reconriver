package io.github.heybadrinath.reconriver.audit;

import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import io.github.heybadrinath.reconriver.workspace.WorkspaceAccess;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
@Validated
class AuditController {
    private final AuditService service;

    AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<AuditEventView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateType) {
        return service.list(
                WorkspaceAccess.currentWorkspaceId(), page, size, eventType, aggregateType);
    }
}
