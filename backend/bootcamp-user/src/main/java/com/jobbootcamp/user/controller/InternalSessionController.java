package com.jobbootcamp.user.controller;

import com.jobbootcamp.contracts.internal.SessionCheckResponse;
import com.jobbootcamp.user.application.SessionValidator;
import com.jobbootcamp.user.infrastructure.RolePermissionMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/int/v1/sessions")
public class InternalSessionController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionValidator sessionValidator;
    private final RolePermissionMapper rolePermissionMapper;

    public InternalSessionController(SessionValidator sessionValidator, RolePermissionMapper rolePermissionMapper) {
        this.sessionValidator = sessionValidator;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @PostMapping("/check")
    public SessionCheckResponse check(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return SessionCheckResponse.inactive();
        }
        return sessionValidator.validate(authorization.substring(BEARER_PREFIX.length()))
                .map(validated -> {
                    List<String> permissions = rolePermissionMapper.selectCodesByRole(validated.user().getRoleCode());
                    return new SessionCheckResponse(
                            true,
                            String.valueOf(validated.user().getId()),
                            validated.user().getRoleCode(),
                            String.valueOf(validated.session().getId()),
                            permissions);
                })
                .orElse(SessionCheckResponse.inactive());
    }
}