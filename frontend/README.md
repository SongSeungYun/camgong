# 캠공 프론트엔드

모바일 우선 웹(PWA). 명세: `specs/008-frontend-web/`.

## 실행

```sh
npm install
npm run dev                         # 실제 백엔드: /api → http://localhost:8080
VITE_API_TARGET=http://host:8080 npm run dev   # 백엔드 주소 변경
VITE_USE_MOCK=true npm run dev      # 백엔드 없이 MSW 목으로 실행
```

## 테스트

```sh
npm test            # Vitest (MSW 목)
npm run e2e         # Playwright (360px 모바일 + 1280px 데스크톱, 목 모드)
npm run typecheck
npm run lint
```

환경 값은 `VITE_USE_MOCK`, `VITE_API_TARGET` 둘뿐이다. 시크릿은 두지 않는다.

## 결정 기록 (2026-10-27, 사용자)

- PWA precache: 아이콘 2개와 `manifest.webmanifest`가 precache에 남는 상태를 그대로 둔다(화면 HTML/JS는 캐시하지 않음).
- `typescript-eslint`·`@eslint/js`는 추가하지 않는다. TS 검사는 `tsc` strict(`npm run typecheck`)로 하고, `lint`는 현 상태(.js만)를 유지한다.

## 점검 기록 (2026-10-28, Phase 10)

### 번들 크기 (T064, `npm run build`)

라우트별 코드 분할 확인: 화면마다 별도 청크(`ListPage`, `DetailPage`, `CheckPage`, `NotificationsPage` 등)로 나뉜다. 목록 첫 화면 측정(SC-006)은 실제 백엔드 연결 후에 하며 지금은 **측정 안 함**.

| 청크 | 크기 (gzip) |
|------|-------------|
| `index` (앱 진입, React·라우터) | 257 kB (81 kB) |
| `ui` (공용 UI·TanStack Query 등) | 86 kB (29 kB) |
| `index.esm` (React Hook Form) | 34 kB (12 kB) |
| `useBaseQuery` | 16 kB (5 kB) |
| `client` | 9 kB (3 kB) |
| 화면 청크 중 가장 큰 `DetailPage` | 7 kB (3 kB) |
| 나머지 화면·훅 청크 | 각 4 kB 이하 |

수치는 빌드 로그 그대로이며, 처음 한 화면을 여는 데 필요한 합계나 실제 로딩 시간은 재지 않았다.

### PWA (T065)

`vite preview`로 서빙한 빌드(목 아님)를 Chromium에서 열어 확인했다.

- 매니페스트: `name/short_name=캠공`, `display=standalone`, `start_url=/`, 아이콘 192·512, `lang=ko`(이번에 `en`에서 수정).
- 서비스 워커 `sw.js`가 등록·활성화된다. `registerType: 'autoUpdate'` + `skipWaiting` + `clientsClaim`이라 새 배포는 새로고침 때 바로 적용된다(옛 서비스 워커가 남아 옛 화면을 붙잡지 않는 구성). 실제 배포 전환은 **확인 못 함**(빌드를 두 번 배포해 본 적 없음).
- 캐시 스토리지: **완전히 비어 있지는 않다.** `workbox-precache`에 `icon-192.png`, `icon-512.png`, `manifest.webmanifest` 3개만 있고 HTML·JS·CSS·API 응답은 없다. 사용자 결정(위)으로 이 상태를 유지한다.
- iOS Safari 홈 화면 추가, "standalone으로 실행", Android 설치 배너는 실제 기기가 필요해 **확인 못 함**.
- `dist/`에 `mockServiceWorker.js`가 들어간다(`public/`에 있음). 목 모드가 아니면 등록하지 않아 동작에는 영향이 없다. 배포물에서 빼고 싶으면 설계 결정이 필요하다.

### 계약 일치 점검 (T066)

실제 백엔드가 떠 있지 않아(`localhost:8080` 응답 없음) 목 없이 다시 돌리는 점검은 **하지 못했다**. 아래는 목 핸들러 응답을 계약 문서와 대조한 결과다.

| 대상 | 결과 |
|------|------|
| 001 개설·수정·취소·상세 (201+Location, 204, 400 `errors[]`, 403, 404, 409 `NOT_MODIFIABLE`+reason, 10초 중복 `DUPLICATE_REQUEST`) | 일치. 목 테스트(`mocks/groupbuys.test.ts`)가 근거 |
| 002 가입·인증·로그인·로그아웃·me (401 `INVALID_CREDENTIALS`, `LOGIN_LOCKED`+retryAt, 재발송 한도, 토큰 오류 3종, `DISPLAY_NAME_TAKEN`) | 일치. `mocks/auth.test.ts` |
| 003 목록·상세·내 활동 (불투명 커서, 400 조건, `myQuantity`, 취소 제외, 노출 규칙) | 일치 |
| 004 참여·수량 변경·참여 취소 (거부 사유 9종, 재시도 안전 200) | 일치. `Participation.test.tsx` |
| 006 체크표·입금/수령 PUT (summary 전체 기준, 멱등, 400/403/404/409) + 003 `myParticipation` 확장 | 일치. `CheckSheet.test.tsx` |
| 007 알림 목록·unread-count·읽음 (수신자 비노출, 남의 알림 404) | 일치. `Notification.test.tsx` |
| 시간 표기 | **차이**: 목은 `toISOString()`(`...Z`)로 내려 주고 계약 예시는 `+09:00`. 같은 시각이고 클라이언트는 어느 쪽이든 KST로 표시·`+09:00`으로 전송 |
| 편집 폼 마감의 초 단위 | 수정 폼이 마감을 분 단위로 자른다. 서버가 초가 있는 마감을 주면 저장 시 초가 사라진다(관제탑 확인 중) |
| 개설자의 `myParticipation.quantity`를 수정 폼의 "내 수량"으로 사용 | 003 백엔드 동작 **확인 못 함** |
| 가입 동의 문구·버전 `v1` | 초안. 사용자가 확정해야 함 |

### 36개 시나리오 (T067, 목 모드)

근거 표기: U=Vitest 통합 테스트, E=Playwright(360·1280px 목 모드), 괄호는 테스트 이름의 번호. "통과"는 해당 테스트가 통과한다는 뜻이며 실제 기기·실제 백엔드 확인은 아니다.

| # | 결과 | 근거 |
|---|------|------|
| 1~6 | 통과 | U(1~6), E(list: 조건·새로고침·스크롤 복원) |
| 7 | 통과 | U(7), E(auth) |
| 8~10 | 통과 | U(8, 9×3, 10×2), E(signup→verify) |
| 11~12 | 통과 | U(11, 6·12) |
| 13 | 통과 | U(13), E(auth: 로그아웃) |
| 14~17 | 통과 | U(14~17), E(create) |
| 15 | 통과(부분) | 기기 시간대 UTC에서 U로 확인. 다른 시간대에서의 E는 안 돌림 |
| 18~20 | 통과 | U(18~20) |
| 21~27 | 통과 | U(21~27), E(join: 22·26) |
| 28 | 통과 | U(28). **브라우저(E)에서는 `/me` 흐름을 안 돌림**(레이아웃만 E로 확인) |
| 29 | 통과 | U(체크표 13개). 브라우저 E는 레이아웃만 |
| 30 | 통과 | U(알림 11개). 브라우저 E는 레이아웃만 |
| 31 | 통과 | U(31), E(auth: 세션 만료) |
| 32 | 부분 | 목록의 오류 + 다시 시도, 클라이언트의 5xx·네트워크 변환은 U로 확인. **폼 제출 중 5xx에서 입력값이 유지되는지는 전용 테스트가 없다** |
| 33~34 | 통과 | U(33, 34) |
| 35 | 통과 | E(layout: S01~S10·404, 긴 제목·긴 이름·긴 알림, 360·1280). 이 점검에서 긴 제목이 가로 스크롤을 만드는 버그를 찾아 고침 |
| 36 | 부분 | 빌드 매니페스트·서비스 워커 등록은 확인. 홈 화면 추가·standalone 실행(iOS 포함)은 **확인 못 함** |

확인 못 한 것: 실제 백엔드와의 연동(Vite 프록시의 `Origin` 검사 포함), 실제 기기(iOS/Android), PWA 설치, 배포 후 자동 업데이트, 목록 첫 화면 로딩 시간(SC-006).
