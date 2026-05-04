# CLAUDE.md

## 테스트 전략

- 기능 추가/수정 시 반드시 검증 테스트를 함께 작성
- 도메인 엔티티 테스트는 순수 단위 테스트 (JPA, Spring Context 의존 금지)
- Service 테스트는 `@SpringBootTest` + `@Transactional` 통합 테스트

## 참조 문서

- `.claude/ai-context/git-convention.md` — Git 커밋 컨벤션
- `.claude/ai-context/architecture.md` — 헥사고날 아키텍처 + DDD 패키지 구조
- `.claude/ai-context/docs/spec.md` — 시스템 요구사항 및 데이터 모델 정의
- `.claude/ai-context/docs/plan.md` — Phase별 점진적 개발 계획
- `.claude/ai-context/docs/tasks.md` — 체크박스 기반 세부 작업 리스트
