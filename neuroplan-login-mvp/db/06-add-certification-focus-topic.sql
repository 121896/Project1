-- 자격증 실기 과목의 시험 영역(예: SQL) 선택을 지원합니다.
-- 기존 일반 과목과 객관식 자격증 과목은 NULL을 유지합니다.

ALTER TABLE user_subjects
    ADD COLUMN focus_topic VARCHAR(100) NULL AFTER learning_level;

SHOW COLUMNS FROM user_subjects LIKE 'focus_topic';
