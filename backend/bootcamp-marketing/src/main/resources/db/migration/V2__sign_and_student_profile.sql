CREATE TABLE sign_record
(
    user_id         BIGINT UNSIGNED NOT NULL,
    business_date   DATE            NOT NULL,
    continuous_days INT             NOT NULL,
    base_points     INT             NOT NULL,
    bonus_points    INT             NOT NULL,
    occurred_at     DATETIME(3)     NOT NULL,
    created_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id, business_date),
    KEY idx_sign_record_date (business_date, user_id),
    CONSTRAINT chk_sign_continuous CHECK (continuous_days >= 1),
    CONSTRAINT chk_sign_points CHECK (base_points >= 0 AND bonus_points >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE student_profile
(
    user_id       BIGINT UNSIGNED NOT NULL,
    nickname      VARCHAR(30)     NOT NULL,
    role_code     VARCHAR(16)     NOT NULL,
    user_revision BIGINT          NOT NULL,
    updated_at    DATETIME(3)     NOT NULL,
    PRIMARY KEY (user_id),
    KEY idx_student_profile_role (role_code, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;