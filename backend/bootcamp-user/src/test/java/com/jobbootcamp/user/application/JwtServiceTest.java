package com.jobbootcamp.user.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.user.config.JwtKeyProvider;
import com.jobbootcamp.user.domain.AuthSession;
import com.jobbootcamp.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void issuesTokenCarryingUserAndSessionClaims() {
        JwtService service = service();
        UserAccount user = user(7L, "STUDENT", 3L);
        AuthSession session = session(11L, 7L);

        JwtService.IssuedToken issued = service.issue(user, session);
        JwtService.TokenClaims claims = service.verify(issued.value());

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.sessionId()).isEqualTo(11L);
        assertThat(claims.tokenVersion()).isEqualTo(3L);
        assertThat(claims.role()).isEqualTo("STUDENT");
        assertThat(issued.expiresIn()).isEqualTo(900L);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = service();
        JwtService.IssuedToken issued = service.issue(user(7L, "STUDENT", 0L), session(11L, 7L));
        String tampered = issued.value().substring(0, issued.value().length() - 2) + "xx";

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(BusinessException.class);
    }

    private JwtService service() {
        JwtKeyProvider keys = new JwtKeyProvider(
                tempDir.resolve("private.pem").toString(),
                tempDir.resolve("public.pem").toString(),
                true);
        return new JwtService(keys, "bootcamp-user", "bootcamp-client", 900);
    }

    private UserAccount user(Long id, String role, Long tokenVersion) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setRoleCode(role);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    private AuthSession session(Long id, Long userId) {
        AuthSession session = new AuthSession();
        session.setId(id);
        session.setUserId(userId);
        return session;
    }
}