# Project1

온프레미스 Kubernetes 운영 구성과 NeuroPlan 학습 서비스 소스 통합 저장소입니다.

## 구성

- `kubespray/`: Kubespray 기반 Main Kubernetes 클러스터 구성, 운영 문서와 점검·장애 대응 자료
- `neuroplan-login-mvp/`: NeuroPlan 웹 서비스 소스
  - `backend/`: Spring Boot 인증·학습·AI API
  - `frontend/`: NGINX 정적 웹 애플리케이션
  - `k8s/`: Kubernetes 매니페스트 및 배포 설정
  - `scripts/`: 배포·Smoke Test 스크립트
- `release-scripts/`: 이미지 빌드·배포 보조 스크립트
- `Jenkinsfile`: CI/CD 파이프라인 정의

## 운영 전제

- Frontend/Backend 이미지는 외부 Harbor Registry(`harbor.nplan.local:80/neuroplan`)를 사용합니다.
- Kubernetes와 DR k3s의 이미지 Pull은 각 클러스터에 미리 등록한 `harbor-pull-secret`으로 수행합니다.
- AI provider는 Gemini API이며, API 키는 `neuroplan-gemini-secrets`의 `GEMINI_API_KEY`로 주입합니다.
- 배포 ConfigMap에는 `LLM_PROVIDER=GEMINI`와 `GEMINI_*` 연결 정보를 사용하며, 기존 Cloudflare endpoint/model/account 설정은 포함하지 않습니다.
- Secret, API 키, 인증서, Ansible Vault 원문은 저장소에 포함하지 않습니다. 배포 전 운영 환경에서 별도로 생성해야 합니다.

## 주의

이 저장소는 소스와 재현 가능한 설정을 보관하기 위한 개인 백업/공유용입니다. 운영 환경의 비밀번호와 토큰은 Git에 커밋하지 말고 Secret 또는 Vault를 사용하세요.
