package io.github.heybadrinath.reconriver.reconciliation;

import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import io.github.heybadrinath.reconriver.workspace.WorkspaceAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconciliation-runs")
@Tag(name = "Reconciliation runs")
@Validated
class ReconciliationRunController {
    private final ReconciliationRunService service;

    ReconciliationRunController(ReconciliationRunService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start a protected, restartable reconciliation batch job")
    RunView create(@Valid @RequestBody(required = false) CreateRunRequest request) {
        Integer controlledFailure = request == null ? null : request.controlledFailureAfterChunks();
        return service.create(WorkspaceAccess.currentWorkspaceId(), controlledFailure);
    }

    @GetMapping
    PageResponse<RunView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        return service.list(WorkspaceAccess.currentWorkspaceId(), page, size, status);
    }

    @GetMapping("/{runId}")
    RunView get(@PathVariable UUID runId) {
        return service.get(WorkspaceAccess.currentWorkspaceId(), runId);
    }

    @GetMapping("/{runId}/progress")
    ProgressView progress(@PathVariable UUID runId) {
        return service.progress(WorkspaceAccess.currentWorkspaceId(), runId);
    }

    @PostMapping("/{runId}/restart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RunView restart(@PathVariable UUID runId) {
        return service.restart(WorkspaceAccess.currentWorkspaceId(), runId);
    }

    record CreateRunRequest(@Min(1) @Max(100000) Integer controlledFailureAfterChunks) {}
}
