# 밤샘 보고서 (2026-10-26)

**브랜치**: `overnight/2026-10-26` (로컬 커밋만, push 안 함). 확인: `git log --oneline main..overnight/2026-10-26`

## 한 일

| 작업 | 결과 | 산출물 |
|------|------|--------|
| J1 | 완료 | 003(35)·004(34)·005(25) `tasks.md` + `docs/overnight/analyze-00N.md` |
| J2 | 완료 | `docs/cross-check-2.md`(Y1–Y13), `checks.py`, `graph-report.md` (graphify 노드 519/엣지 907, 추적성 감사에는 부정확해 스크립트를 기준으로 삼음) |
| J3 | 완료 | 006·007·002 plan/research/data-model/contracts/quickstart 초안 (결정 30개 모두 대기) |
| J4 | 완료 | `docs/spikes.md`에 'Spring 수준 검증' 절 (Boot 4.1.2, JDK 25, 테스트 13/13 통과) |

건너뛴 작업: 없음.

## 아침에 결정할 것

[decisions-pending.md](decisions-pending.md) 한 곳에 모았다. 추천대로면 "추천대로"라고만 해도 된다.

## 가장 중요한 발견 (설계에 영향)

1. **005 SKIP LOCKED** (정정 2026-10-27: `ORDER BY … LIMIT 1` 범위 선점일 때만 해당. 005 tasks의 id 지정 선점은 REPEATABLE READ에서도 통과해 설계 변경 불필요): MySQL 기본 격리 수준(REPEATABLE READ)에서는 범위 선점 워커 두 개가 동시에 처리하면 `status` 갱신이 락 대기로 실패한다. READ COMMITTED에서는 통과. 원인은 갭 락으로 **추정**(락 상태를 직접 보지는 않음), 2행짜리 최소 재현. → 결정 대기 #6
2. **004 재시도 vs FR-009 모호성**(analyze-004 B1) → 결정 대기
3. **직접 UPDATE에는 `version = version + 1` 필수**(없으면 갱신 손실 재현됨)
4. 인덱스는 `CREATE INDEX IF NOT EXISTS`가 아닌 `KEY` 절로(MySQL 미지원으로 알고 있음, 구현 때 확인)
5. 002 spec 보완 제안 5건(비밀번호 최대 길이 등), 008 spec 범위가 낡음(Y2)

## 한계 / 확인 못 한 것

- 스파이크는 JDK 25 + Boot 4.1.2로만 돌림(프로젝트 목표 Java 21). Java 21 재검증 필요.
- 락 경합 빈도, 이스케이프 미적용 동작, `schema.sql` 재기동 재실행은 확인 못 함.
- 메일 서버 스팸 처리, BCrypt 비용별 로그인 시간은 측정하지 않음.
- 사용 토큰: J2 graphify 하위 에이전트 약 37.6만. 전체 합계는 측정하지 못함.

## 다음 단계 제안

1. 결정 대기 목록 처리 → 해당 plan 확정
2. 설명 체크(17개 질문), 수요 확인(단계 0)
3. 브랜치 병합 여부 결정(push는 직접 승인)
4. 확정 후 002·006·007 `/speckit-tasks`
