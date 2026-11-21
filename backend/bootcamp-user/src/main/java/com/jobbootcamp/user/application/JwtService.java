package com.jobbootcamp.user.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.user.config.JwtKeyProvider;
import com.jobbootcamp.user.domain.AuthSession;
import com.jobbootcamp.user.domain.UserAccount;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * RS256 access token 的签发与校验。tokenVersion 与会话 ID 一并写入，
 * 资源服务通过会话校验即可让改密、禁用或重放撤销立即生效。
 */
@Service
public class JwtService {

    private final JwtKeyProvider keyProvider;
    private final String issuer;
    private final String audience;
    private final long accessTtlSeconds;

    public JwtService(
            JwtKeyProvider keyProvider,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.access-ttl-seconds}") long accessTtlSeconds) {
        this.keyProvider = keyProvider;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public record IssuedToken(String value, long expiresIn) {
    }

    public record TokenClaims(long userId, long sessionId, long tokenVersion, String role) {
    }

    public IssuedToken issue(UserAccount user, AuthSession session) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRoleCode())
                .claim("sessionId", String.valueOf(session.getId()))
                .claim("tokenVersion", user.getTokenVersion())
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTtlSeconds)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(keyProvider.getPrivateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
        return new IssuedToken(jwt.serialize(), accessTtlSeconds);
    }

    public TokenClaims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw unauthorized();
            }
            if (!jwt.verify(new RSASSAVerifier(keyProvider.getPublicKey()))) {
                throw unauthorized();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer())) {
                throw unauthorized();
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
                throw unauthorized();
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                throw unauthorized();
            }
            String sessionId = claims.getStringClaim("sessionId");
            String role = claims.getStringClaim("role");
            Long tokenVersion = claims.getLongClaim("tokenVersion");
            if (sessionId == null || role == null || tokenVersion == null) {
                throw unauthorized();
            }
            return new TokenClaims(
                    Long.parseLong(claims.getSubject()),
                    Long.parseLong(sessionId),
                    tokenVersion,
                    role);
        } catch (ParseException | JOSEException | RuntimeException e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw unauthorized();
        }
    }

    private BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
    }
}