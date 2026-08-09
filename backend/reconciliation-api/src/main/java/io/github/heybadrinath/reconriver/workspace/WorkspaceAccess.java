package io.github.heybadrinath.reconriver.workspace;

import io.github.heybadrinath.reconriver.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class WorkspaceAccess {
    private WorkspaceAccess() {}

    public static UUID currentWorkspaceId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof WorkspacePrincipal principal)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "SESSION_REQUIRED",
                    "Start a demo workspace before continuing.");
        }
        return principal.workspaceId();
    }
}
