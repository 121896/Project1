-- 정보처리기사 실기 AI 주관식 출제·답안 검토 지원 마이그레이션입니다.
-- 운영 Primary에서 논리 백업 후 한 번만 실행하고, Replica에는 GTID 복제로 전파합니다.
-- 애플리케이션 배포는 이 SQL 적용 및 Replica 정상 복제 확인 이후 진행합니다.

ALTER TABLE diagnosis_questions
    ADD COLUMN question_type VARCHAR(20) NOT NULL DEFAULT 'MULTIPLE_CHOICE' AFTER difficulty,
    ADD COLUMN reference_answer TEXT NULL AFTER explanation,
    ADD COLUMN accepted_answers_json JSON NULL AFTER reference_answer,
    ADD COLUMN grading_rubric TEXT NULL AFTER accepted_answers_json,
    ADD CONSTRAINT chk_questions_type
        CHECK (question_type IN ('MULTIPLE_CHOICE', 'SHORT_ANSWER'));

ALTER TABLE diagnosis_answers
    MODIFY COLUMN selected_option_id BIGINT UNSIGNED NULL,
    ADD COLUMN answer_text TEXT NULL AFTER selected_option_id,
    ADD COLUMN evaluation_detail JSON NULL AFTER is_correct,
    ADD COLUMN evaluated_by VARCHAR(20) NULL AFTER evaluation_detail,
    ADD COLUMN evaluated_at DATETIME(6) NULL AFTER evaluated_by;

-- 기존 문제는 객관식으로 유지하며, 주관식은 정보처리기사 실기에서만 애플리케이션이 생성합니다.
UPDATE diagnosis_questions
   SET question_type = 'MULTIPLE_CHOICE'
 WHERE question_type IS NULL;

-- 적용 확인
SHOW COLUMNS FROM diagnosis_questions LIKE 'question_type';
SHOW COLUMNS FROM diagnosis_questions LIKE 'reference_answer';
SHOW COLUMNS FROM diagnosis_questions LIKE 'accepted_answers_json';
SHOW COLUMNS FROM diagnosis_questions LIKE 'grading_rubric';
SHOW COLUMNS FROM diagnosis_answers LIKE 'answer_text';
SHOW COLUMNS FROM diagnosis_answers LIKE 'evaluation_detail';
SHOW COLUMNS FROM diagnosis_answers LIKE 'evaluated_by';
SHOW COLUMNS FROM diagnosis_answers LIKE 'evaluated_at';

SELECT constraint_name
  FROM information_schema.table_constraints
 WHERE table_schema = DATABASE()
   AND table_name = 'diagnosis_questions'
   AND constraint_name = 'chk_questions_type';
