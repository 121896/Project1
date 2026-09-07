-- AI 문제은행 중복 차단용 마이그레이션입니다.
-- 적용 전 DB 백업을 만들고, Primary에서 한 번만 실행하세요.
-- 기존 문제는 애플리케이션이 문제·보기 내용을 기준으로 함께 비교하므로
-- content_hash가 NULL이어도 신규 AI 문제와의 중복 생성은 차단됩니다.

ALTER TABLE diagnosis_questions
    ADD COLUMN content_hash CHAR(64) NULL AFTER question_text;

CREATE UNIQUE INDEX uk_questions_subject_content_hash
    ON diagnosis_questions (subject_id, content_hash);

-- 적용 확인
SHOW COLUMNS FROM diagnosis_questions LIKE 'content_hash';
SHOW INDEX FROM diagnosis_questions WHERE Key_name = 'uk_questions_subject_content_hash';
