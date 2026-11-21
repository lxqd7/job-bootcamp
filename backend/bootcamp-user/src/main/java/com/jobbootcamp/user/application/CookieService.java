package com.jobbootcamp.user.application;

import com.jobbootcamp.user.domain.ClientApp;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 学生端与管理端使用独立的 refresh/CSRF Cookie 名，避免同一浏览器登录两端时互相覆盖。
 * refresh Cookie 仅发送到 /api/v1/auth 且 HttpOnly；CSRF Cookie 由前端读取后放入请求头。
 */
@Component
public class CookieService {

    public static final String CSRF_HEADER = "X-CSRF-Token";
    public static final String CLIENT_APP_HEADER = "X-Client-App";

    private final boolean secure;

    public CookieService(@Value("${app.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    public String refreshCookieName(ClientApp clientApp) {
        return clientApp == ClientApp.STUDENT ? "bc_student_refresh" : "bc_admin_refresh";
    }

    public String csrfCookieName(ClientApp clientApp) {
        return clientApp == ClientApp.STUDENT ? "bc_student_csrf" : "bc_admin_csrf";
    }

    public void writeSessionCookies(HttpServletResponse response, ClientApp clientApp,
                                    String refreshToken, String csrfToken, long maxAgeSeconds) {
        addCookie(response, refreshCookieName(clientApp), refreshToken, "/api/v1/auth", true, maxAgeSeconds);
        addCookie(response, csrfCookieName(clientApp), csrfToken, "/", false, maxAgeSeconds);
    }

    public void writeRefreshCookie(HttpServletResponse response, ClientApp clientApp,
                                   String refreshToken, long maxAgeSeconds) {
        addCookie(response, refreshCookieName(clientApp), refreshToken, "/api/v1/auth", true, maxAgeSeconds);
    }

    public void clearSessionCookies(HttpServletResponse response, ClientApp clientApp) {
        addCookie(response, refreshCookieName(clientApp), "", "/api/v1/auth", true, 0);
        addCookie(response, csrfCookieName(clientApp), "", "/", false, 0);
    }

    public String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String name, String value,
                           String path, boolean httpOnly, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}