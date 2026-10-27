# Data Model: 모바일 우선 웹 화면

프론트는 저장소가 없다. 아래는 화면이 다루는 **상태와 뷰 모델**이다. 서버 응답의 필드 이름과 값은 `specs/*/contracts/`가 기준이고, 여기서는 화면이 어떻게 소비하는지만 적는다. 추천안 기준(`research.md`, 2026-10-27 확정).

## 1. 화면 상태 (View state)

모든 데이터 화면이 공통으로 가진다(FR-007). 쿼리 상태에서 파생한다.

| 상태 | 조건 | 표시 |
|------|------|------|
| `loading` | 첫 로딩 중 | 골격(skeleton) 또는 로딩 표시 |
| `empty` | 성공했고 항목 0개 | 빈 목록 / 검색 결과 없음을 구분 (FR-014: `q`, 카테고리, 기본값(OPEN)이 아닌 상태 중 하나라도 걸려 있으면 후자, 정렬은 조건이 아니다) |
| `error` | 네트워크·5xx | 다시 시도 버튼이 있는 오류 안내, 입력값 유지 |
| `ready` | 성공 | 정상 |
| `fetchingMore` | '더 보기' 중 | 목록 유지 + 버튼 비활성화 |

## 2. 세션 (Session)

| 필드 | 설명 |
|------|------|
| `status` | `checking`(me 조회 중) / `anonymous` / `authenticated` |
| `member` | `{ id, displayName }` — 이 둘만 보관 (이메일·비밀번호 보관 안 함, FR-019) |

- 앱 시작 시 `GET /api/auth/me`로 `status`를 정한다. 401이면 `anonymous`.
- 어떤 요청이든 401을 받으면 `anonymous`로 바꾸고 쿼리 캐시를 전부 비운 뒤 로그인으로 보낸다(FR-003). 로그아웃도 같다.

## 3. 공구 뷰 모델

### 카드 (목록·내 활동)

`id, title, itemName, category, unitPrice, securedQuantity, totalQuantity, deadline, status`(+ 내 활동 `participant`일 때 `myQuantity`). 파생 값(화면이 계산): 진행률 = `securedQuantity / totalQuantity`, 마감까지 남은 시간(불러온 시점 기준), 금액 포맷. **표시 상태는 서버 `status`를 그대로 쓴다**(FR-008).

### 상세

`id, title, description, itemName, category, itemUrl, unitPrice, totalQuantity, securedQuantity, remainingQuantity, deadline, status, organizerName, isOrganizer, participantCount, myParticipation, openChatUrl, participants, createdAt` (+ 006 확장: 참여자 본인의 `paidConfirmedAt`, `receivedConfirmedAt`). 화면은 `isOrganizer`, `myParticipation`, `status`, `openChatUrl`, `participants`의 **존재 여부**로 버튼과 영역을 정한다.

### 표시 상태 (status)

| 서버 값 | 뱃지 | 비고 |
|---------|------|------|
| `OPEN` | 모집 중 | |
| `PENDING_CLOSE` | 마감됨(확정 대기) | |
| `COMPLETED` | 모집 완료 | 오픈채팅 링크는 `openChatUrl`이 있을 때만 |
| `FAILED` | 모집 실패 | |
| 그 외 | 알 수 없음(일반 표시) | FR-008, 화면이 멈추지 않는다 |

### 상세 영역 노출 규칙 (FR-021, FR-022)

| 영역 | 보이는 조건 |
|------|-------------|
| 수정·취소 버튼 | `isOrganizer` 이고 상태가 `OPEN` |
| 참여 버튼 | `!isOrganizer`, `myParticipation == null`, 상태 `OPEN` |
| 수량 변경·참여 취소 | `myParticipation != null`, 상태 `OPEN` |
| 오픈채팅 입장 | `openChatUrl != null` (화면이 조건을 재판단하지 않음) |
| 참여자 명단 | `participants != null` (개설자) |
| 체크표 이동 | `isOrganizer` 이고 상태 `COMPLETED` |
| 내 입금·수령 확인 상태 | `myParticipation`이 있고 `COMPLETED` (006 확장) |

버튼 노출은 편의이고 최종 허용은 서버 응답을 따른다.

## 4. 목록 조건 (URL 상태, 주소 쿼리)

| 키 | 값 | 기본 | 비고 |
|----|-----|------|------|
| `status` | `OPEN`, `PENDING_CLOSE`, `COMPLETED`, `FAILED` | `OPEN` | |
| `category` | `FOOD`, `DAILY`, `KITCHEN`, `HEALTH_BEAUTY`, `STATIONERY`, `ETC` | 전체 | 표시 이름: 식품·생활용품·주방·건강뷰티·문구학용품·기타 |
| `q` | 문자열(50자 이하) | 없음 | 앞뒤 공백 제거, 공백뿐이면 검색 안 함, 디바운스 |
| `sort` | `latest`, `deadline` | `latest` | |

커서(`cursor`)는 주소에 넣지 않는다(쿼리 캐시가 관리). 조건이 바뀌면 커서를 버린다. 알 수 없는 값은 기본값으로 되돌린다.

## 5. 폼 모델

### 개설·수정 폼 (S05·S06)

필드 이름은 계약(001 요청 본문)과 같다: `title, description, category, itemName, itemUrl, unitPrice, totalQuantity, organizerQuantity, deadline, openChatUrl`. 서버 오류의 `errors[].field`는 이 이름과 1:1로 대응한다. 마감 입력은 KST `datetime-local`이고 전송 시 `+09:00` 오프셋 ISO 문자열로 바꾼다(FR-004). 안내용 범위: 제목 100자, 개당 가격 1~1,000,000, 총 수량 2~1,000, 내 수량 1 이상 총 수량 미만, 마감 지금부터 30일 이내, 구매처·오픈채팅은 https. **최종 판단은 서버.** 미리보기: 내 부담 금액 = `unitPrice × organizerQuantity`.

### 참여 폼 (S07)

`quantity`(1 이상 남은 수량 이하 정수)와 화면이 확인한 `expectedUnitPrice`. 가격 변경으로 거부(`PRICE_CHANGED`)되면 `currentUnitPrice`로 갱신하고 사용자가 다시 확인해야 제출 가능.

### 가입·로그인 폼 (S01·S02)

- 가입 폼(S01, `/signup`): `email`만. 서버 필드 이름 `email`과 같게 둔다.
- 인증 폼(`/verify`): `displayName, password, consent, consentVersion`(+ URL의 `token`, 002 계약 verify 본문). 비밀번호는 `type=password`, 화면 상태에만 있고 저장하지 않는다. 오류 시에도 비밀번호 입력값을 로그·주소·저장소에 남기지 않는다(오류 뒤 화면 상태 유지는 허용).
- 로그인 폼(S02): `email, password`.

## 6. 오류 모델 (ApiError)

서버 `ProblemDetail`을 이렇게 바꾼다.

| 필드 | 설명 |
|------|------|
| `status` | HTTP 상태 |
| `code` | 본문 `title`(예: `NOT_JOINABLE`) 또는 없음 |
| `reason` | 본문 `reason`(예: `DEADLINE_PASSED`) 또는 없음 |
| `fieldErrors` | `errors[{field,message}]` |
| 추가 속성 | `currentUnitPrice`, `remainingQuantity`, `retryAt` 등 코드별 |

사용자에게는 `ui-api-map.md`의 문구표로 바꾼 말만 보인다(FR-006). 서버의 `detail`/스택은 노출하지 않는다.
