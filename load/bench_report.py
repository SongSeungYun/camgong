#!/usr/bin/env python3
"""bench_collect 가 쌓은 CSV 를 방식별 표(markdown)로 요약한다. 사용: bench_report.py <last-seat.csv> <s2.csv> <mixed.csv>
앞 2회는 JIT 워밍업이라 뺀다(혼합 부하는 그대로)."""
import csv, statistics, sys

GATES = ['none', 'pessimistic', 'optimistic', 'conditional']


def load(path, skip):
    rows = list(csv.DictReader(open(path)))
    out = {g: [r for r in rows if r['gate'] == g and int(r['rep']) > skip] for g in GATES}
    return out


def f(v):
    return float(v)


def pct(values, q):
    values = sorted(values)
    return values[min(len(values) - 1, int(round(q * (len(values) - 1))))]


def lat(rows):
    return [f(r['p50']) for r in rows], [f(r['p95']) for r in rows], [f(r['p99']) for r in rows]


def table_last_seat(data):
    print('| 방식 | 반복 | 성공(201) 정확히 1건인 반복 | 성공 수 합계 | I1 위반 반복 | 완료 전환 ≠ 1회 반복 | 5xx 합계 | BUSY 합계 | 재시도 합계 | p50 ms (중앙) | p95 ms (중앙/최악) | p99 ms (중앙/최악) |')
    print('|------|------|-----------|-------------|--------------|-------------------|---------|----------|------------|---------------|------------------|------------------|')
    for g in GATES:
        rs = data[g]
        if not rs:
            continue
        exact = sum(1 for r in rs if int(r['r201']) == 1)
        succ = sum(int(r['r201']) for r in rs)
        i1 = sum(1 for r in rs if int(r['i1_violations']) > 0 or int(r['secured']) != int(r['participation_sum']))
        comp = sum(1 for r in rs if int(r['completed_events']) != 1)
        e5 = sum(int(r['r5xx']) for r in rs)
        busy = sum(int(r['r409_BUSY']) for r in rs)
        retries = sum(int(r['retries']) for r in rs)
        p50, p95, p99 = lat(rs)
        print(f"| {g} | {len(rs)} | {exact} | {succ} | {i1} | {comp} | {e5} | {busy} | {retries} | {statistics.median(p50):.1f} | {statistics.median(p95):.1f} / {max(p95):.1f} | {statistics.median(p99):.1f} / {max(p99):.1f} |")


def table_reasons(data):
    print('| 방식 | 201 | 409 NOT_JOINABLE | 409 INSUFFICIENT_QUANTITY | 409 BUSY | 5xx | 기타 |')
    print('|------|-----|-----------------|---------------------------|---------|-----|------|')
    for g in GATES:
        rs = data[g]
        if not rs:
            continue
        s = lambda k: sum(int(r[k]) for r in rs)
        other = s('r400') + s('r401') + s('r403') + s('r404') + s('r200') + s('r204') + s('rother') + s('r409_PRICE_CHANGED') + s('r409_ALREADY_JOINED') + s('r409_other')
        print(f"| {g} | {s('r201')} | {s('r409_NOT_JOINABLE')} | {s('r409_INSUFFICIENT_QUANTITY')} | {s('r409_BUSY')} | {s('r5xx')} | {other} |")


def table_s2(data):
    print('| 방식 | 반복 | 초과(Σ수량 > 24) 반복 | I1 위반 반복 | 수용 수량 합 > 10 반복 | 평균 수용 수량 | 완료 전환 횟수 합계 | 5xx 합계 | BUSY 합계 | 재시도 합계 | p50 ms | p95 ms (중앙/최악) | p99 ms (중앙/최악) |')
    print('|------|------|------------------|--------------|------------------|----------------|--------------------|---------|----------|------------|--------|------------------|------------------|')
    for g in GATES:
        rs = data[g]
        if not rs:
            continue
        over = sum(1 for r in rs if int(r['participation_sum']) > 24)
        i1 = sum(1 for r in rs if int(r['i1_violations']) > 0 or int(r['secured']) != int(r['participation_sum']))
        acc = [int(r['participation_sum']) - 14 for r in rs]
        over10 = sum(1 for a in acc if a > 10)
        comp = sum(int(r['completed_events']) for r in rs)
        e5 = sum(int(r['r5xx']) for r in rs)
        busy = sum(int(r['r409_BUSY']) for r in rs)
        retries = sum(int(r['retries']) for r in rs)
        p50, p95, p99 = lat(rs)
        print(f"| {g} | {len(rs)} | {over} | {i1} | {over10} | {statistics.mean(acc):.2f} | {comp} | {e5} | {busy} | {retries} | {statistics.median(p50):.1f} | {statistics.median(p95):.1f} / {max(p95):.1f} | {statistics.median(p99):.1f} / {max(p99):.1f} |")


def table_mixed(data):
    print('| 방식 | 반복 | 요청 합계 | 처리량 req/s (평균) | p50 ms | p95 ms | p99 ms | 2xx | 409 NOT_JOINABLE | 409 INSUFFICIENT | 409 BUSY | 5xx | 재시도 합계 | I1 위반 반복 |')
    print('|------|------|----------|-------------------|--------|--------|--------|-----|-----------------|------------------|---------|-----|------------|--------------|')
    for g in GATES:
        rs = data[g]
        if not rs:
            continue
        s = lambda k: sum(int(r[k]) for r in rs)
        p50, p95, p99 = lat(rs)
        ok = s('r200') + s('r201') + s('r204')
        i1 = sum(1 for r in rs if int(r['i1_violations']) > 0 or int(r['secured']) != int(r['participation_sum']))
        print(f"| {g} | {len(rs)} | {s('requests')} | {statistics.mean(f(r['rps']) for r in rs):.0f} | {statistics.mean(p50):.1f} | {statistics.mean(p95):.1f} | {statistics.mean(p99):.1f} | {ok} | {s('r409_NOT_JOINABLE')} | {s('r409_INSUFFICIENT_QUANTITY')} | {s('r409_BUSY')} | {s('r5xx')} | {s('retries')} | {i1} |")


ls, s2, mx = sys.argv[1:4]
d1, d2, d3 = load(ls, 2), load(s2, 2), load(mx, 0)
print('### S1 마지막 자리 (남은 1, 100명 동시)\n'); table_last_seat(d1); print('\n거부 사유(합계)\n'); table_reasons(d1)
print('\n### S2 다양한 수량 (남은 10, 50명 동시, 수량 1~5)\n'); table_s2(d2)
print('\n### 혼합 부하 (20 VU 참여·변경·취소 + 개설자 수정, 30초)\n'); table_mixed(d3)
