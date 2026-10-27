#!/usr/bin/env bash
# 004 수량 게이트 비교 측정 전체(헌법 II): 마지막 자리 32회(앞 2회는 워밍업), S2 32회, 혼합 부하 5회 x 30초.
# 사용: load/run-all.sh <결과 디렉터리>   (먼저 ./gradlew bootJar)
set -uo pipefail
OUT=${1:?결과 디렉터리}
mkdir -p "$OUT"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
"$ROOT/load/run-bench.sh" last-seat 32 "none pessimistic optimistic conditional" "$OUT/last-seat.csv" > "$OUT/run1.log" 2>&1
"$ROOT/load/run-bench.sh" s2 32 "none pessimistic optimistic conditional" "$OUT/s2.csv" > "$OUT/run2.log" 2>&1
"$ROOT/load/run-bench.sh" mixed 5 "none pessimistic optimistic conditional" "$OUT/mixed.csv" 30s > "$OUT/run3.log" 2>&1
echo finished > "$OUT/DONE"
