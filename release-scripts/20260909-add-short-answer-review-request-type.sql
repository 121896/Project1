-- MariaDB primary에서 한 번 실행합니다. Replica에는 직접 실행하지 않습니다.
-- AI 주관식 채점 이력에만 사용하는 정확한 유형을 허용 목록에 추가합니다.
ALTER TABLE ai_generation_runs
    DROP CONSTRAINT chk_ai_runs_request_type;

ALTER TABLE ai_generation_runs
    ADD CONSTRAINT chk_ai_runs_request_type
    CHECK (request_type IN (
        'PLAN',
        'WRONG_FEEDBACK',
        'WEEKLY_INSIGHT',
        'QUESTION_DRAFT',
        'SHORT_ANSWER_REVIEW'
    ));
