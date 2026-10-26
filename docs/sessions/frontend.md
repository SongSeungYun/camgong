# 프론트엔드 세션 시작 지시

이 저장소(`camgong`)의 프론트엔드를 구현하는 작업 세션이다. 먼저 `docs/sessions/README.md`를 읽고 규칙(소유 범위, 승인 요약, 절대 규칙)을 따른다.

- 브랜치: `frontend/008-list`에서 시작한다(없으면 만든다). 기능 묶음이 바뀌면 `frontend/008-<묶음>`처럼 새 브랜치를 쓴다.
- 소유 범위: `frontend/`만 고친다. `specs/`·`docs/`는 읽기만 한다.
- 대상: `specs/008-frontend-web/tasks.md`를 T001부터 순서대로. 태스크 하나가 끝날 때마다 승인 요약을 보여주고 사용자의 승인을 받은 뒤 커밋한다. 커밋은 `git add frontend/<경로>`로 고른 경로만, 접두사 `feat:`/`test:`/`chore:`.
- 먼저 읽을 것: `specs/008-frontend-web/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/ui-api-map.md`, `quickstart.md`, `tasks.md`와 서버 계약 `specs/001~007/contracts/`, 오류 코드 표 `docs/architecture.md` 6.1, 화면 흐름 `docs/screens.md`.
- 확정된 기술 선택: React 19 + Vite + TypeScript(strict), React Router, TanStack Query, React Hook Form, CSS Modules, MSW, Vitest + Testing Library, Playwright, `vite-plugin-pwa`(오프라인 캐시 없음). 이 밖의 의존성은 추가하기 전에 사용자에게 이유를 한 줄로 알린다.
- 인증: 세션 쿠키(`credentials: 'include'`). `X-Member-Id`는 쓰지 않는다. CSRF 토큰 헤더 없음(002 계약).
- 백엔드는 아직 없을 수 있다. 목(MSW)이 계약대로 응답하게 만들고, 목이 계약에서 벗어나지 않게 `ui-api-map.md`와 대조한다.
- 불확실한 것을 확인했다고 쓰지 않는다(iOS PWA 설치, 실제 백엔드 성능 등은 "확인 못 함").
- 계약·spec이 모호하거나 서로 충돌하면 임의로 정하지 말고 멈춰서 사용자에게 알린다.
- 이 기능에는 `(핵심 로직)` 태스크가 없다. 승인 요약에는 diff 요지, 테스트 결과, 계약과 달라진 점(없으면 "없음")을 적는다.
