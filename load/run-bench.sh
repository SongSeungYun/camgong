#!/usr/bin/env bash
# 수량 게이트 4방식을 같은 시나리오로 비교한다(헌법 II).
# 사용: load/run-bench.sh <scenario: last-seat|s2|mixed> <reps> "<gates>" <csv> [duration]
# 전제: docker, java, ./gradlew bootJar 로 만든 jar. MySQL 컨테이너와 앱은 이 스크립트가 띄우고 정리한다.
set -euo pipefail
SCENARIO=$1; REPS=$2; GATES=$3; CSV=$4; DURATION=${5:-30s}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAR=$(ls "$ROOT"/build/libs/*.jar | grep -v plain | head -1)
DB=camgong-bench-mysql; PORT=18997; DBPORT=33310
OUT=$(mktemp -d)
case $SCENARIO in
  last-seat) TOTAL=24; ORG=23 ;;
  s2) TOTAL=24; ORG=14 ;;
  mixed) TOTAL=24; ORG=9 ;;
esac

mysql_exec() { docker exec -i $DB mysql -uroot -ppw camgong "$@" 2>/dev/null; }

docker rm -f $DB >/dev/null 2>&1 || true
docker run -d --name $DB -p $DBPORT:3306 -e MYSQL_ROOT_PASSWORD=pw -e MYSQL_DATABASE=camgong mysql:8.4 >/dev/null
until docker exec $DB mysqladmin ping -uroot -ppw --silent 2>/dev/null; do sleep 2; done

start_app() {
  DB_URL="jdbc:mysql://localhost:$DBPORT/camgong?useAffectedRows=true" nohup java -Xms512m -Xmx512m -jar "$JAR" \
    --spring.profiles.active=benchmark --camgong.quantity-gate="$1" --server.port=$PORT > "$OUT/app-$1.log" 2>&1 &
  APP_PID=$!
  until curl -s -o /dev/null "127.0.0.1:$PORT/bench/stats"; do sleep 1; done
}
stop_app() { kill "$APP_PID" 2>/dev/null || true; wait "$APP_PID" 2>/dev/null || true; }

first=1
for gate in $GATES; do
  start_app "$gate"
  if [ $first = 1 ]; then mysql_exec < "$ROOT/load/seed.sql"; first=0; fi
  for rep in $(seq 1 "$REPS"); do
    (echo "SET @TOTAL=$TOTAL, @ORG=$ORG;"; cat "$ROOT/load/reset.sql") | mysql_exec
    curl -s -X POST "127.0.0.1:$PORT/bench/reset" >/dev/null
    docker run --rm -e BASE_URL="http://host.docker.internal:$PORT" -e DURATION="$DURATION" -e TOTAL=$TOTAL \
      -v "$ROOT/load/k6:/s" -v "$OUT:/out" grafana/k6 run --quiet --summary-export /out/summary.json "/s/$SCENARIO.js" >/dev/null 2>"$OUT/k6.err" || true
    curl -s "127.0.0.1:$PORT/bench/stats" > "$OUT/stats.json"
    mysql_exec -N -e "SELECT g.secured_quantity, g.total_quantity, g.status, COALESCE((SELECT SUM(quantity) FROM participation WHERE group_buy_id = 1), 0), (SELECT COUNT(*) FROM (SELECT g2.id FROM group_buy g2 LEFT JOIN participation p ON p.group_buy_id = g2.id GROUP BY g2.id, g2.secured_quantity HAVING g2.secured_quantity <> COALESCE(SUM(p.quantity), 0)) v) FROM group_buy g WHERE g.id = 1" > "$OUT/db.tsv"
    python3 "$ROOT/load/bench_collect.py" "$CSV" "$gate" "$SCENARIO" "$rep" "$OUT/summary.json" "$OUT/stats.json" "$OUT/db.tsv"
  done
  stop_app
done
docker rm -f $DB >/dev/null
echo "done: $CSV"
