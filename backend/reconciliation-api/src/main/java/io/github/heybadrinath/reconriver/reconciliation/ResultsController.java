package io.github.heybadrinath.reconriver.reconciliation;

import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import io.github.heybadrinath.reconriver.workspace.WorkspaceAccess;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconciliation-runs/{runId}")
@Validated
class ResultsController {
    private final ResultsService service;

    ResultsController(ResultsService service) {
        this.service = service;
    }

    @GetMapping("/results")
    PageResponse<ResultView> results(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String resolutionStatus) {
        return service.list(
                WorkspaceAccess.currentWorkspaceId(),
                runId,
                page,
                size,
                outcome,
                ruleCode,
                resolutionStatus);
    }

    @GetMapping("/summary")
    SummaryView summary(@PathVariable UUID runId) {
        return service.summary(WorkspaceAccess.currentWorkspaceId(), runId);
    }

    @GetMapping(value = "/report.csv", produces = "text/csv")
    @Operation(summary = "Download a spreadsheet-safe CSV report")
    ResponseEntity<byte[]> report(@PathVariable UUID runId) {
        byte[] report = service.report(WorkspaceAccess.currentWorkspaceId(), runId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("reconciliation-" + runId + ".csv")
                                .build()
                                .toString())
                .body(report);
    }
}
