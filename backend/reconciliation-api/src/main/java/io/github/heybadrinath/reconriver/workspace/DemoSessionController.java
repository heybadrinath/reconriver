package io.github.heybadrinath.reconriver.workspace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/sessions")
@Tag(name = "Demo sessions")
class DemoSessionController {
    private final DemoSessionService service;

    DemoSessionController(DemoSessionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start an isolated 24-hour demonstration workspace")
    DemoSessionService.SessionCreated create() {
        return service.create();
    }
}
