package io.github.heybadrinath.reconriver.dataset;

import io.github.heybadrinath.reconriver.workspace.WorkspaceAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/scenarios")
@Tag(name = "Prepared scenarios")
class ScenarioController {
    private final ScenarioService service;

    ScenarioController(ScenarioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List deterministic synthetic scenarios")
    ScenarioService.ListResponse list() {
        return service.list();
    }

    @GetMapping("/{slug}")
    ScenarioService.ScenarioView get(@PathVariable String slug) {
        return service.get(slug);
    }

    @GetMapping("/{slug}/files/{fileName:.+}")
    ResponseEntity<byte[]> download(@PathVariable String slug, @PathVariable String fileName) {
        ScenarioService.ScenarioFile file = service.download(slug, fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName())
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }

    @PostMapping("/{slug}/load")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Load a scenario through the same import pipeline used by uploads")
    ScenarioService.LoadResponse load(@PathVariable String slug) {
        return service.load(WorkspaceAccess.currentWorkspaceId(), slug);
    }
}
