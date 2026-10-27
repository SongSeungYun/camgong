#!/usr/bin/env python3
"""k6 요약(JSON)과 DB·앱 지표를 읽어 CSV 한 줄로 덧붙인다. 사용: bench_collect.py <csv> <gate> <scenario> <rep> <summary.json> <stats.json> <db.tsv>"""
import json, sys

csv, gate, scenario, rep, summary, stats, db = sys.argv[1:8]
s = json.load(open(summary))['metrics']
st = json.load(open(stats))
secured, total, status, rows_sum, i1 = [x for x in open(db).read().split()]


def count(name):
    return int(s.get(name, {}).get('count', 0))


names = ['r200', 'r201', 'r204', 'r400', 'r401', 'r403', 'r404', 'r409_NOT_JOINABLE', 'r409_INSUFFICIENT_QUANTITY',
         'r409_BUSY', 'r409_PRICE_CHANGED', 'r409_ALREADY_JOINED', 'r409_other', 'r5xx', 'rother']
d = s['http_req_duration']
reqs = s['http_reqs']
row = [gate, scenario, rep] + [count(n) for n in names] + [
    round(d['med'], 2), round(d['p(95)'], 2), round(d['p(99)'], 2), round(reqs['rate'], 1), reqs['count'],
    st['retries'], st['busy'], st['completedEvents'], secured, total, status, rows_sum, i1]
header = ['gate', 'scenario', 'rep'] + names + ['p50', 'p95', 'p99', 'rps', 'requests', 'retries', 'busy', 'completed_events',
                                                  'secured', 'total', 'status', 'participation_sum', 'i1_violations']
import os
new = not os.path.exists(csv)
with open(csv, 'a') as f:
    if new:
        f.write(','.join(header) + '\n')
    f.write(','.join(str(x) for x in row) + '\n')
