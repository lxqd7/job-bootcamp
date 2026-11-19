CREATE TABLE user_account
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)     NOT NULL,
    password_hash VARCHAR(100)    NOT NULL,
    nickname      VARCHAR(30)     NOT NULL,
    role_code     VARCHAR(32)     NOT NULL,
    enabled       BOOLEAN         NOT NULL DEFAULT TRUE,
    token_version BIGINT          NOT NULL DEFAULT 0,
    avatar_url    VARCHAR(1000)   NULL,
    created_at    DATETIME(3)     NOT NULL,
    updated_at    DATETIME(3)     NOT NULL,
    revision      BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_username (username),
    KEY idx_user_account_role (role_code),
    CONSTRAINT chk_user_account_role CHECK (role_code IN ('STUDENT', 'TEACHER', 'ADMIN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE role_permission
(
    role_code       VARCHAR(32) NOT NULL,
    permission_code VARCHAR(80) NOT NULL,
    PRIMARY KEY (role_code, permission_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE auth_session
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    client_app      VARCHAR(16)     NOT NULL,
    refresh_hash    CHAR(64)        NOT NULL,
    expires_at      DATETIME(3)     NOT NULL,
    revoked_at      DATETIME(3)     NULL,
    last_rotated_at DATETIME(3)     NOT NULL,
    csrf_hash       CHAR(64)        NOT NULL,
    created_at      DATETIME(3)     NOT NULL,
    updated_at      DATETIME(3)     NOT NULL,
    revision        BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_session_refresh_hash (refresh_hash),
    KEY idx_auth_session_user (user_id, revoked_at),
    KEY idx_auth_session_expires (expires_at),
    CONSTRAINT chk_auth_session_client CHECK (client_app IN ('STUDENT', 'ADMIN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_history
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    used_hash  CHAR(64)        NOT NULL,
    used_at    DATETIME(3)     NOT NULL,
    created_at DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_history_used_hash (used_hash),
    KEY idx_refresh_history_session (session_id, used_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;