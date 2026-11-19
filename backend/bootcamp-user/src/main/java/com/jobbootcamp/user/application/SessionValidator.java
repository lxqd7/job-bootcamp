package com.jobbootcamp.user.application;

import com.jobbootcamp.user.domain.AuthSession;
import com.jobbootcamp.user.domain.UserAccount;
import com.jobbootcamp.user.infrastructure.AuthSessionMapper;
import com.jobbootcamp.user.infrastructure.UserAccountMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
public class SessionValidator {

    private final JwtService jwtService;
    private final AuthSessionMapper sessionMapper;
    private final UserAccountMapper userMapper;

    public SessionValidator(JwtService jwtService, AuthSessionMapper sessionMapper, UserAccountMapper userMapper) {
        this.jwtService = jwtService;
        this.sessionMapper = sessionMapper;
        this.userMapper = userMapper;
    }

    public record ValidatedSession(AuthSession session, UserAccount user) {
    }

    public Optional<ValidatedSession> validate(String accessToken) {
        try {
            JwtService.TokenClaims claims = jwtService.verify(accessToken);
            AuthSession session = sessionMapper.selectById(claims.sessionId());
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            if (session == null
                    || session.getRevokedAt() != null
                    || session.getExpiresAt().isBefore(now)
                    || !session.getUserId().equals(claims.userId())) {
                return Optional.empty();
            }
            UserAccount user = userMapper.selectById(claims.userId());
            if (user == null
                    || !Boolean.TRUE.equals(user.getEnabled())
                    || user.getTokenVersion().longValue() != claims.tokenVersion()
                    || !user.getRoleCode().equals(claims.role())) {
                return Optional.empty();
            }
            return Optional.of(new ValidatedSession(session, user));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}