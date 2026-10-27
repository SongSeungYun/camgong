# Contract: 마감 확정의 이벤트와 설정

외부로 열리는 HTTP API는 없다(백그라운드 처리). 이 기능이 내놓는 인터페이스는 이벤트, 설정, 로그 세 가지다.

## 이벤트

| 이벤트 | 필드 | 발행 시점 | 소비 |
|--------|------|-----------|------|
| `GroupBuyFailed` | `eventId`(발행 때마다 새 UUID), `groupBuyId`, `groupBuyTitle`, `recipientIds`(개설자 포함) | 공구가 모집 실패로 확정되어 커밋된 직후(`AFTER_COMMIT`) | 알림 기능(007). 지금은 `GroupBuyEventLogger`가 로그만 남김 |

- `recipientIds`: 개설자를 포함한 모든 참여 행의 회원 id. 중복 없음.
- 공구 하나당 정확히 한 번 발행한다(중복 금지). 커밋 직후 서버가 멈추면 유실될 수 있다(허용된 한계).
- 확정 전에 롤백되면 발행하지 않는다.

## 설정 (`application.yml`)

| 키 | 기본값 | 뜻 |
|----|--------|-----|
| `camgong.scheduler.close.enabled` | `true` | 스케줄러 켜기/끄기. 테스트는 `false`로 두고 서비스를 직접 호출 |
| `camgong.scheduler.close.interval` | `30s` | 이전 실행이 끝난 뒤 다음 실행까지의 대기 |
| `camgong.scheduler.close.margin` | `1s` | 마감 시각 뒤 확정까지의 안전 여유(시계 오차 대비) |
| `camgong.scheduler.close.batch-size` | `100` | 한 라운드의 후보 조회 개수 |
| `camgong.scheduler.close.max-rounds` | `50` | 한 실행에서 반복하는 최대 라운드 수 |

## 로그

| 레벨 | 형식 | 언제 |
|------|------|------|
| INFO | `close-run closed=12 skipped=3 failed=1 rounds=2 durationMs=420` | 후보가 있었던 실행이 끝날 때 |
| WARN | `close-failed groupBuyId=42 reason=…` | 한 공구의 확정이 예외로 실패했을 때(다음 실행에서 재시도) |

`skipped`는 선점 쿼리가 빈 결과를 돌려준 공구 수(다른 인스턴스가 잡았거나 이미 확정됨)다.

## 다른 기능과의 계약

- **003**: `FAILED`를 '모집 실패'로 표시하고 필터로 노출한다. 오픈채팅 링크는 공개하지 않는다.
- **004·001**: `status != 'OPEN'`이라 참여·수정·취소가 거부된다. 마감 시각 ≤ 현재부터 이미 거부되므로 확정 전 '마감됨(확정 대기)' 구간에도 변경이 막혀 있다.
- **007**: `GroupBuyFailed`를 받아 알림을 전달한다.
