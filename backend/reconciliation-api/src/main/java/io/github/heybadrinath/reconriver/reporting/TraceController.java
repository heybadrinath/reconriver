package io.github.heybadrinath.reconriver.reporting;

import io.github.heybadrinath.reconriver.workspace.WorkspaceAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transaction trace")
class TraceController {
    private final TraceService service;

    TraceController(TraceService service) {
        this.service = service;
    }

    @GetMapping("/{transactionId}/trace")
    TransactionTraceView trace(@PathVariable UUID transactionId) {
        return service.trace(WorkspaceAccess.currentWorkspaceId(), transactionId);
    }
}
