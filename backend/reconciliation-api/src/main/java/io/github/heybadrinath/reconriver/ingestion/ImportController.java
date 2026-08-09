package io.github.heybadrinath.reconriver.ingestion;

import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import io.github.heybadrinath.reconriver.workspace.DemoSessionService;
import io.github.heybadrinath.reconriver.workspace.WorkspaceAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Imports")
@Validated
class ImportController {
    private final ImportService service;

    ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Validate, stage and start a restartable CSV import",
            description = DemoSessionService.UPLOAD_WARNING)
    ImportView upload(
            @RequestParam SourceType sourceType,
            @Parameter(description = "CSV only; maximum 10 MB and 25,000 data rows") @RequestParam
                    MultipartFile file) {
        return service.upload(WorkspaceAccess.currentWorkspaceId(), sourceType, file);
    }

    @GetMapping
    PageResponse<ImportView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status) {
        return service.list(WorkspaceAccess.currentWorkspaceId(), page, size, sourceType, status);
    }

    @GetMapping("/{importId}")
    ImportView get(@PathVariable UUID importId) {
        return service.get(WorkspaceAccess.currentWorkspaceId(), importId);
    }

    @GetMapping("/{importId}/errors")
    PageResponse<ImportRowErrorView> errors(
            @PathVariable UUID importId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return service.errors(WorkspaceAccess.currentWorkspaceId(), importId, page, size);
    }
}
