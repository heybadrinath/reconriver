package io.github.heybadrinath.reconriver.investigation;

import io.github.heybadrinath.reconriver.reconciliation.ResultView;
import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import io.github.heybadrinath.reconriver.workspace.WorkspaceAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exceptions")
@Tag(name = "Exception workbench")
@Validated
class InvestigationController {
    private final InvestigationService service;

    InvestigationController(InvestigationService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ResultView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String status) {
        return service.list(
                WorkspaceAccess.currentWorkspaceId(), page, size, runId, outcome, status);
    }

    @GetMapping("/{exceptionId}")
    ExceptionDetailView get(@PathVariable UUID exceptionId) {
        return service.get(WorkspaceAccess.currentWorkspaceId(), exceptionId);
    }

    @PostMapping("/{exceptionId}/resolve")
    ResultView resolve(@PathVariable UUID exceptionId, @Valid @RequestBody NoteRequest request) {
        return service.resolve(WorkspaceAccess.currentWorkspaceId(), exceptionId, request.note());
    }

    @PostMapping("/{exceptionId}/reopen")
    ResultView reopen(@PathVariable UUID exceptionId, @Valid @RequestBody NoteRequest request) {
        return service.reopen(WorkspaceAccess.currentWorkspaceId(), exceptionId, request.note());
    }

    record NoteRequest(@NotBlank @Size(min = 3, max = 1000) String note) {}
}
