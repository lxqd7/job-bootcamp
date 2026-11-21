package com.jobbootcamp.user.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.user.domain.AuthSession;
import com.jobbootcamp.user.domain.ClientApp;
import com.jobbootcamp.user.domain.RefreshHistory;
import com.jobbootcamp.user.domain.RoleCode;
import com.jobbootcamp.user.domain.UserAccount;
import com.jobbootcamp.user.domain.dto.ChangePasswordRequest;
import com.jobbootcamp.user.domain.dto.LoginRequest;
import com.jobbootcamp.user.domain.dto.LoginResponse;
import com.jobbootcamp.user.domain.dto.RefreshResponse;
import com.jobbootcamp.user.domain.dto.RegisterRequest;
import com.jobbootcamp.user.domain.dto.RegisterResponse;
import com.jobbootcamp.user.domain.dto.UserSummary;
import com.jobbootcamp.user.infrastructure.AuthSessionMapper;
import com.jobbootcamp.user.infrastructure.RefreshHistoryMapper;
import com.jobbootcamp.user.infrastructure.UserAccountMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 账号与会话生命周期。refresh token 只以哈希落库并单次轮换：
 * 已使用过的 refresh 再次出现视为令牌泄漏，撤销该用户全部活跃会话并递增 token_version。
 */
@Service
public class AuthService {

    private final UserAccountMapper userMapper;
    private final AuthSessionMapper sessionMapper;
    private final RefreshHistoryMapper historyMapper;
    private final TokenHasher tokenHasher;
    private final JwtService jwtService;
    private final CookieService cookieService;
    private final PasswordEncoder passwordEncoder;
    private final long refreshTtlSeconds;

    public AuthService(UserAccountMapper userMapper,
                       AuthSessionMapper sessionMapper,
                       RefreshHistoryMapper historyMapper,
                       TokenHasher tokenHasher,
                       JwtService jwtService,
                       CookieService cookieService,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.jwt.refresh-ttl-seconds}") long refreshTtlSeconds) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.historyMapper = historyMapper;
        this.tokenHasher = tokenHasher;
        this.jwtService = jwtService;
        this.cookieService = cookieService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        validatePassword(request.password());
        Long existing = userMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, request.username()));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        LocalDateTime now = utcNow();
        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setRoleCode(RoleCode.STUDENT.name());
        user.setEnabled(true);
        user.setTokenVersion(0L);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setRevision(0L);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        return new RegisterResponse(String.valueOf(user.getId()));
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        UserAccount user = userMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw unauthorized("用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw unauthorized("账号不可用");
        }
        if (!request.clientApp().allowsRole(user.getRoleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该账号不能登录当前客户端");
        }

        LocalDateTime now = utcNow();
        String refreshToken = tokenHasher.randomToken();
        String csrfToken = tokenHasher.randomToken();
        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setClientApp(request.clientApp().name());
        session.setRefreshHash(tokenHasher.sha256(refreshToken));
        session.setExpiresAt(now.plusSeconds(refreshTtlSeconds));
        session.setLastRotatedAt(now);
        session.setCsrfHash(tokenHasher.sha256(csrfToken));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setRevision(0L);
        sessionMapper.insert(session);

        JwtService.IssuedToken token = jwtService.issue(user, session);
        cookieService.writeSessionCookies(response, request.clientApp(), refreshToken, csrfToken, refreshTtlSeconds);
        return new LoginResponse(
                token.value(),
                token.expiresIn(),
                new UserSummary(String.valueOf(user.getId()), user.getNickname(), user.getRoleCode()),
                csrfToken);
    }

    @Transactional(noRollbackFor = SessionInvalidatedException.class)
    public RefreshResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        ClientApp clientApp = requireClientApp(request);
        String refreshToken = cookieService.readCookie(request, cookieService.refreshCookieName(clientApp));
        if (refreshToken == null) {
            throw unauthorized("登录已失效，请重新登录");
        }
        String refreshHash = tokenHasher.sha256(refreshToken);
        // 行锁把同一会话的并发刷新串行化：只有一个请求能消费旧 refresh token。
        AuthSession session = sessionMapper.selectOne(Wrappers.<AuthSession>lambdaQuery()
                .eq(AuthSession::getRefreshHash, refreshHash)
                .last("FOR UPDATE"));
        if (session == null) {
            handleReplay(refreshHash);
        }
        if (!clientApp.name().equals(session.getClientApp())) {
            throw unauthorized("登录已失效，请重新登录");
        }
        LocalDateTime now = utcNow();
        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(now)) {
            throw unauthorized("登录已失效，请重新登录");
        }
        requireCsrf(request, session);

        UserAccount user = userMapper.selectById(session.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            LocalDateTime revokedAt = utcNow();
            session.setRevokedAt(revokedAt);
            session.setUpdatedAt(revokedAt);
            session.setRevision(session.getRevision() + 1);
            sessionMapper.updateById(session);
            throw new SessionInvalidatedException("账号不可用");
        }

        String newRefreshToken = tokenHasher.randomToken();
        RefreshHistory history = new RefreshHistory();
        history.setSessionId(session.getId());
        history.setUsedHash(refreshHash);
        history.setUsedAt(now);
        history.setCreatedAt(now);
        historyMapper.insert(history);

        session.setRefreshHash(tokenHasher.sha256(newRefreshToken));
        session.setExpiresAt(now.plusSeconds(refreshTtlSeconds));
        session.setLastRotatedAt(now);
        session.setUpdatedAt(now);
        session.setRevision(session.getRevision() + 1);
        sessionMapper.updateById(session);

        JwtService.IssuedToken token = jwtService.issue(user, session);
        cookieService.writeRefreshCookie(response, clientApp, newRefreshToken, refreshTtlSeconds);
        return new RefreshResponse(token.value(), token.expiresIn());
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        ClientApp clientApp = requireClientApp(request);
        String refreshToken = cookieService.readCookie(request, cookieService.refreshCookieName(clientApp));
        if (refreshToken != null) {
            AuthSession session = sessionMapper.selectOne(Wrappers.<AuthSession>lambdaQuery()
                    .eq(AuthSession::getRefreshHash, tokenHasher.sha256(refreshToken)));
            if (session != null) {
                if (!clientApp.name().equals(session.getClientApp())) {
                    throw unauthorized("登录已失效，请重新登录");
                }
                requireCsrf(request, session);
                if (session.getRevokedAt() == null) {
                    LocalDateTime now = utcNow();
                    session.setRevokedAt(now);
                    session.setUpdatedAt(now);
                    session.setRevision(session.getRevision() + 1);
                    sessionMapper.updateById(session);
                }
            }
        }
        cookieService.clearSessionCookies(response, clientApp);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null) {
            throw unauthorized("登录已失效，请重新登录");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "原密码不正确");
        }
        validatePassword(request.newPassword());

        LocalDateTime now = utcNow();
        userMapper.update(null, Wrappers.<UserAccount>lambdaUpdate()
                .set(UserAccount::getPasswordHash, passwordEncoder.encode(request.newPassword()))
                .setSql("token_version = token_version + 1")
                .set(UserAccount::getUpdatedAt, now)
                .setSql("revision = revision + 1")
                .eq(UserAccount::getId, userId));
        // 改密后旧会话全部失效；只撤销未撤销的行，保留历史撤销时间。
        sessionMapper.update(null, Wrappers.<AuthSession>lambdaUpdate()
                .set(AuthSession::getRevokedAt, now)
                .set(AuthSession::getUpdatedAt, now)
                .setSql("revision = revision + 1")
                .eq(AuthSession::getUserId, userId)
                .isNull(AuthSession::getRevokedAt));
    }

    private void handleReplay(String refreshHash) {
        RefreshHistory history = historyMapper.selectOne(Wrappers.<RefreshHistory>lambdaQuery()
                .eq(RefreshHistory::getUsedHash, refreshHash));
        if (history != null) {
            AuthSession oldSession = sessionMapper.selectById(history.getSessionId());
            if (oldSession != null) {
                // 旧令牌重放说明可能泄漏：撤销该用户所有会话并让现有 access token 立即失效。
                LocalDateTime now = utcNow();
                sessionMapper.update(null, Wrappers.<AuthSession>lambdaUpdate()
                        .set(AuthSession::getRevokedAt, now)
                        .set(AuthSession::getUpdatedAt, now)
                        .setSql("revision = revision + 1")
                        .eq(AuthSession::getUserId, oldSession.getUserId())
                        .isNull(AuthSession::getRevokedAt));
                userMapper.update(null, Wrappers.<UserAccount>lambdaUpdate()
                        .setSql("token_version = token_version + 1")
                        .set(UserAccount::getUpdatedAt, now)
                        .setSql("revision = revision + 1")
                        .eq(UserAccount::getId, oldSession.getUserId()));
            }
            throw new SessionInvalidatedException("刷新令牌已被使用，会话已失效");
        }
        throw new SessionInvalidatedException("登录已失效，请重新登录");
    }

    private ClientApp requireClientApp(HttpServletRequest request) {
        String value = request.getHeader(CookieService.CLIENT_APP_HEADER);
        try {
            return ClientApp.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "X-Client-App 缺失或不合法");
        }
    }

    private void requireCsrf(HttpServletRequest request, AuthSession session) {
        String csrfToken = request.getHeader(CookieService.CSRF_HEADER);
        if (!tokenHasher.matches(csrfToken, session.getCsrfHash())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "CSRF 校验失败");
        }
    }

    private void validatePassword(String password) {
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        if (length < 8 || length > 72) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码长度需为 8-72 字节");
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCode.UNAUTHORIZED, message);
    }
}