-- 자격증 과목 기준정보 시드
-- 기존 subjects 구조를 유지하면서 시험 단계를 별도 과목 코드로 분리합니다.
-- 일반 과목의 초급·중급·고급 학습 흐름과 사용자 기록에는 영향을 주지 않습니다.

INSERT INTO subjects (code, name, is_active, created_at)
SELECT seed.code, seed.name, TRUE, CURRENT_TIMESTAMP(6)
FROM (
  SELECT 'INFORMATION_PROCESSING_WRITTEN' AS code, '정보처리기사 · 필기' AS name
  UNION ALL SELECT 'INFORMATION_PROCESSING_PRACTICAL', '정보처리기사 · 실기'
  UNION ALL SELECT 'LINUX_MASTER_2_FIRST', '리눅스마스터 2급 · 1차'
  UNION ALL SELECT 'LINUX_MASTER_2_SECOND', '리눅스마스터 2급 · 2차'
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM subjects existing WHERE existing.code = seed.code
);

-- 적용 확인
SELECT id, code, name, is_active
  FROM subjects
 WHERE code IN (
   'INFORMATION_PROCESSING_WRITTEN',
   'INFORMATION_PROCESSING_PRACTICAL',
   'LINUX_MASTER_2_FIRST',
   'LINUX_MASTER_2_SECOND'
 )
 ORDER BY code;
