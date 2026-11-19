package com.jobbootcamp.user.controller;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.user.application.AuthService;
import com.jobbootcamp.user.domain.dto.LoginRequest;
import com.jobbootcamp.user.domain.dto.LoginResponse;
import com.jobbootcamp.user.domain.dto.RefreshResponse;
import com.jobbootcamp.user.domain.dto.RegisterRequest;
import com.jobbootcamp.user.domain.dto.RegisterResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletResponse response) {
        return ApiResponse.ok(authService.login(request, response));
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(HttpServletRequest request,
                                                HttpServletResponse response) {
        return ApiResponse.ok(authService.refresh(request, response));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.ok(null);
    }
}