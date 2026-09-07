-- 학습 운영 기능(알림, 문제 신고, 문제은행 자동 보충, 선택 플랜) 마이그레이션입니다.
-- DB Primary에서 백업 후 한 번만 실행합니다.

CREATE TABLE user_notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    source_key VARCHAR(100) NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    target_page VARCHAR(30) NOT NULL DEFAULT 'dashboard',
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_user_source (user_id, source_key),
    KEY ix_notifications_user_read_created (user_id, is_read, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE question_reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    report_type VARCHAR(30) NOT NULL,
    detail VARCHAR(500) NULL,
    report_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution_note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    resolved_by_user_id BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    KEY ix_reports_status_created (report_status, created_at),
    KEY ix_reports_question (question_id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_user_id) REFERENCES users(id),
    CONSTRAINT fk_reports_question FOREIGN KEY (question_id) REFERENCES diagnosis_questions(id),
    CONSTRAINT fk_reports_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES users(id),
    CONSTRAINT chk_reports_type CHECK (report_type IN ('INCORRECT', 'AMBIGUOUS', 'DUPLICATE', 'OTHER')),
    CONSTRAINT chk_reports_status CHECK (report_status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
) ENGINE=InnoDB;

CREATE TABLE ai_problem_bank_settings (
    id TINYINT UNSIGNED NOT NULL,
    is_enabled TINYINT(1) NOT NULL DEFAULT 0,
    owner_user_id BIGINT UNSIGNED NULL,
    low_water_mark SMALLINT UNSIGNED NOT NULL DEFAULT 20,
    target_count SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    last_run_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_problem_bank_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT chk_problem_bank_levels CHECK (low_water_mark BETWEEN 5 AND 500 AND target_count BETWEEN 5 AND 1000 AND target_count >= low_water_mark)
) ENGINE=InnoDB;

INSERT INTO ai_problem_bank_settings (id, is_enabled, owner_user_id, low_water_mark, target_count, updated_at)
VALUES (1, 0, NULL, 20, 30, CURRENT_TIMESTAMP(6));

CREATE TABLE user_active_plans (
    user_id BIGINT UNSIGNED NOT NULL,
    subject_id BIGINT UNSIGNED NOT NULL,
    plan_id BIGINT UNSIGNED NOT NULL,
    selected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, subject_id),
    KEY ix_active_plans_plan (plan_id),
    CONSTRAINT fk_active_plans_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_active_plans_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_active_plans_plan FOREIGN KEY (plan_id) REFERENCES daily_plans(id)
) ENGINE=InnoDB;

-- 적용 확인
SHOW TABLES LIKE 'user_notifications';
SHOW TABLES LIKE 'question_reports';
SHOW TABLES LIKE 'ai_problem_bank_settings';
SHOW TABLES LIKE 'user_active_plans';
