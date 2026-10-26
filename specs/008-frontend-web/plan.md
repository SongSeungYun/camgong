# Implementation Plan: 모바일 우선 웹 화면

**Branch**: `008-frontend-web` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-frontend-web/spec.md`

> **상태**: 방식(모바일 우선 웹 + PWA)과 스택(React + Vite + TypeScript)은 2026-10-27 사용자가 확정했다. 나머지 설계 결정은 [research.md](research.md)의 **추천안 기준 가안**이며 사용자 결정 대기다. spec에 빠진 부분(S09·S10의 FR, PWA 문구 충돌, 계약 공백)은 research.md 맨 앞 '스펙·계약 보완 필요'에 모았다.

## Summary

기숙사생이 에브리타임 글의 링크를 휴대폰으로 열어 쓰는 **모바일 우선 웹 앱(PWA, 홈 화면에 추가 가능)**. 화면 S01~S10을 001~007의 REST 계약 위에 구현한다. 서버는 바꾸지 않는다.

- 단일 페이지 앱(React + Vite + TypeScript), 같은 저장소의 `frontend/`.
- 백엔드 없이 개발할 수 있게 **계약대로 응답하는 목(MSW)**을 둔다. 같은 시나리오(quickstart)를 나중에 실제 백엔드로 다시 돌려 목이 계약에서 벗어나지 않았는지 확인한다.
- 인증은 002의 세션 쿠키다. 화면은 로그인 여부와 표시 이름만 알고, 서버가 401을 주면 로그인으로 보낸다. `X-Member-Id`는 쓰지 않는다.
- 서버가 계산한 값을 그대로 쓴다(표시 상태, 공개 규칙, 오픈채팅 링크·명단 노출). 화면은 판단하지 않고 보여주기만 한다.

## Technical Context

**Language/Version**: TypeScript 5.x (strict), Node.js LTS (개발·빌드 전용)

**Primary Dependencies**: React 19, Vite, React Router, TanStack Query, React Hook Form, `vite-plugin-pwa`. 테스트: Vitest, Testing Library, MSW, Playwright. 각 선택의 이유(헌법 기술 제약: 새 의존성은 이유 한 줄)는 research.md 결정 3~9에 있다. **전부 사용자 결정 대기.**

**Storage**: 없음. 서버가 진실 원천이다. 브라우저에는 목록 조건(주소 쿼리)과 로그인 쿠키(서버가 설정, 스크립트 접근 불가)만 있다. 비밀번호·토큰을 `localStorage`에 저장하지 않는다(FR-019).

**Testing**: Vitest + Testing Library(화면 단위, MSW로 계약 응답), Playwright(360px 뷰포트 핵심 시나리오 3~4개). 헌법 IV 필수 대상은 백엔드 로직이라 프론트 테스트는 선택이지만, 계약 일치 확인을 위해 최소한을 둔다.

**Target Platform**: 휴대폰 기본 브라우저(iOS Safari, Android Chrome) 최근 2년, 데스크톱 최신 Chrome. 설치형은 PWA(홈 화면 추가)까지.

**Project Type**: web-application (frontend only, 기존 REST API 소비)

**Performance Goals**: SC-006 — 4G에서 목록 첫 화면 3초 이내, 조건 변경 결과 1초 이내(서버 응답 포함). 초기 번들은 화면 단위 코드 분할로 작게 유지한다(수치 목표는 측정 후 정함, 확인 못 함).

**Constraints**: 360px 폭에서 가로 스크롤 없음(SC-003), 모든 시각 한국 시각 표시·오프셋 포함 전송(FR-004), 서버 텍스트는 문자 그대로 렌더링(FR-010, React 기본 이스케이프에 `dangerouslySetInnerHTML` 금지), 외부 링크는 https만(FR-009), 오프라인 캐시는 하지 않는다(spec 가정: 오프라인 제외, 낡은 화면 방지).

**Scale/Scope**: 화면 10개(S01~S10), 사용자 수백~수천 명, 화면 코드 약 40~60개 파일 규모(추정).

## Constitution Check

*GATE: Phase 0 전 통과 필요. Phase 1 설계 후 재확인.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. 범위 상한 | **통과 (경고)** | 화면은 헌법상 "일정이 막히면 먼저 줄이는" 항목이다. S09·S10까지 포함하면 범위가 늘었으므로, 막히면 S10 → S09 → S08 순으로 줄인다(research.md 결정 14). PWA는 홈 화면 추가까지만(오프라인 제외)이라 추가 비용이 작다 |
| II. 측정으로 증명 | 통과 (해당 없음) | 프론트는 비교 측정 대상이 아니다. SC-006 수치는 4단계에서 한 번 잰다 |
| III. 설명 가능한 설계 | 통과 (결정 대기) | 결정 14개 모두 후보·추천·설명 체크가 있다. 확정은 사용자 몫 |
| IV. 핵심 로직 테스트 | 통과 (해당 없음) | 필수 대상은 백엔드. 프론트는 계약 일치 확인용 최소 테스트 |
| 기술 제약: 새 의존성은 이유를 plan에 한 줄 | 대기 | 각 의존성의 이유는 research.md. 사용자 승인 후 확정 |
| 개발 워크플로: 설계 결정은 작성자가 직접 | 통과 | 스택·방식은 사용자가 정했고 나머지는 결정 대기로 남겼다 |
| 보안: 시크릿·저장소 공개 | 통과 | 프론트에 시크릿이 없다. 환경 값은 `VITE_` 공개 값만(예: API 기본 경로) |

**Post-design 재확인**: 위 판정에서 바뀐 것 없음. 단 PWA 문구 충돌(spec "PWA 제외")과 CSRF 계약 공백은 spec·계약을 고치기 전까지 구현 착수 조건으로 남는다.

## Project Structure

### Documentation (this feature)

```text
specs/008-frontend-web/
├── plan.md
├── research.md          # 결정 14개 + 스펙·계약 보완 필요
├── data-model.md        # 화면 상태·뷰 모델·URL 상태
├── quickstart.md        # 화면별 검증 시나리오 (목 → 실제 백엔드)
├── contracts/
│   └── ui-api-map.md    # 화면 ↔ 엔드포인트 ↔ 오류 → 안내 문구 매핑
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### Source Code (repository root)

```text
frontend/
├── index.html
├── vite.config.ts                # 개발 프록시 /api → 백엔드, PWA 플러그인
├── public/                       # manifest 아이콘
├── src/
│   ├── app/                      # 라우터, 레이아웃, 에러 경계, 인증 가드
│   ├── pages/                    # S01~S10 화면 (라우트 단위, 지연 로딩)
│   ├── features/
│   │   ├── auth/                 # 로그인·가입·인증·세션 상태
│   │   ├── groupbuy/             # 목록·상세·개설·수정·취소·내 활동
│   │   ├── participation/        # 참여·수량 변경·취소
│   │   ├── check/                # 체크표 (006)
│   │   └── notification/         # 알림함 (007)
│   ├── shared/
│   │   ├── api/                  # fetch 래퍼, 오류 파싱(ProblemDetail), 계약 타입
│   │   ├── ui/                   # 버튼·폼 필드·뱃지·빈/오류/로딩 상태·확인 대화상자
│   │   └── lib/                  # 시각(KST) 변환, 금액, 외부 링크 검사, 오류 → 문구
│   └── mocks/                    # MSW 핸들러(계약 응답), 시나리오 데이터
└── tests/
    ├── unit/                     # 화면·훅 (Vitest + Testing Library + MSW)
    └── e2e/                      # Playwright 360px
```

**Structure Decision**: 기능별 폴더(`features/*`)에 그 기능의 훅·컴포넌트·API 호출을 모으고, 화면 라우트는 `pages/`가 조립만 한다. 공통 코드는 `shared/`에 두되 기능을 알지 못한다. 백엔드의 `groupbuy/join/close/check` 하위 패키지 나눔과 같은 기준(기능 단위)이다.

## Complexity Tracking

| 항목 | 이유 | 더 단순한 안을 안 쓴 이유 |
|------|------|--------------------------|
| 목(MSW) 서버 + 실제 백엔드 재확인 | 백엔드가 늦어도 프론트가 진행되고, 두 세션이 병행된다 | 백엔드를 기다리면 병행이 사라진다. 목이 계약에서 벗어날 위험은 quickstart의 재확인으로 막는다 |
| TanStack Query 도입 | 더 보기(무한 목록), 변경 후 무효화(FR-030), 재시도 정책을 직접 짜면 코드가 커진다 | 수제 fetch 훅은 화면 10개에서 같은 코드가 반복된다 |
