#!/usr/bin/env bash
# 014 T003: 맥 쪽 지우는 규칙(plan 결정 4)과 backup.sh 의 장부 복사(결정 3)를 로컬 디렉터리로 확인한다. 서버·Docker 는 쓰지 않는다.
# curl·docker 는 PATH 앞의 가짜로 바꿔 부른 인자만 적는다. 사용: bash deploy/offsite-pull.test.sh
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
t="$(mktemp -d)"
trap 'rm -rf "$t"' EXIT

mkdir "$t/bin"
cat > "$t/bin/curl" <<'EOF'
#!/bin/sh
for a; do last=$a; done
echo "$last" >> "$PINGS"
EOF
# docker compose exec -T <서비스> ...: mysql 이면 덤프, backend 면 $LEDGER 를 내보낸다(없으면 cat 처럼 실패).
cat > "$t/bin/docker" <<'EOF'
#!/bin/sh
case "$4" in
	mysql) echo "-- dump" ;;
	backend) cat "$LEDGER" ;;
	*) exit 2 ;;
esac
EOF
chmod +x "$t/bin/curl" "$t/bin/docker"
export PATH="$t/bin:$PATH" PINGS="$t/pings"

fails=0
check() { if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fails=$((fails + 1)); fi; }
old() { touch -t "$(date -v-7d +%Y%m%d%H%M)" "$@"; }       # 7일 전: 6일 규칙에 걸린다
recent() { touch -t "$(date -v-2d +%Y%m%d%H%M)" "$@"; }    # 2일 전: 남는다

# 맥 스크립트를 원천 $1, 보관 $2 로 돌리고 종료 코드를 $t/rc 에 남긴다.
pull() {
	printf 'SOURCE=%s\nDEST=%s\nHC_PING_URL=https://hc.example/mac\n' "$1" "$2" > "$t/conf"
	: > "$PINGS"
	rc=0; CAMGONG_OFFSITE_CONF="$t/conf" bash "$here/offsite-pull.sh" > "$t/out" 2>&1 || rc=$?
}

# 1. 새 덤프가 왔고 7일 넘은 사본이 있다 → 그 사본만 지운다
src="$t/1/src"; dst="$t/1/dst"; mkdir -p "$src" "$dst"
echo new > "$src/camgong-20261126-040000.sql.gz"; echo l2 > "$src/withdraw-ledger.tsv"
echo writing > "$src/camgong-20261126-050000.sql.gz.tmp"
: > "$src/camgong-20261124-040000.sql.gz"   # 서버에서 같은 이름이 망가졌다(털림·디스크 오류)
echo a > "$dst/camgong-20261119-040000.sql.gz"; old "$dst/camgong-20261119-040000.sql.gz"
echo b > "$dst/camgong-20261124-040000.sql.gz"; recent "$dst/camgong-20261124-040000.sql.gz"
echo l1 > "$dst/withdraw-ledger.tsv"
chmod 755 "$dst"
echo half > "$dst/.camgong-20261125-040000.sql.gz.tmp"   # 지난번에 옮기다 멈춘 것
mkdir -p "$dst/.incoming"; echo half > "$dst/.incoming/.camgong-20261125-040000.sql.gz.Ab12"   # rsync 가 받다 멈춘 것
echo gone > "$dst/.incoming/camgong-20261118-040000.sql.gz"   # 서버에서 이미 지운 것
pull "$src/" "$dst"
check "1 성공" '[ $rc -eq 0 ]'
check "1 새 덤프 받음" '[ -f "$dst/camgong-20261126-040000.sql.gz" ]'
check "1 7일 넘은 사본 지움" '[ ! -e "$dst/camgong-20261119-040000.sql.gz" ]'
check "1 2일 된 사본 남음, 서버 내용으로 덮어쓰지 않음" '[ "$(cat "$dst/camgong-20261124-040000.sql.gz")" = b ]'
check "1 장부 덮어씀" '[ "$(cat "$dst/withdraw-ledger.tsv")" = l2 ]'
check "1 쓰는 중인 .tmp 는 안 받음" '[ -z "$(find "$dst" -name "camgong-20261126-050000.sql.gz.tmp")" ]'
check "1 성공 알림" '[ "$(cat "$PINGS")" = https://hc.example/mac ]'
check "1 보관 디렉터리·파일은 소유자만" '[ "$(stat -f %Lp "$dst")" = 700 ] && [ "$(stat -f %Lp "$dst/camgong-20261126-040000.sql.gz")" = 600 ] && [ "$(stat -f %Lp "$dst/withdraw-ledger.tsv")" = 600 ]'
check "1 남은 임시 파일 없음" '[ -z "$(find "$dst" -name ".camgong-*")" ]'
check "1 거울은 서버를 따라 지운다" '[ ! -e "$dst/.incoming/camgong-20261118-040000.sql.gz" ]'

# 2. 원천이 비었다 → 아무것도 안 지운다. 2일 안의 덤프가 없어 실패 알림
src="$t/2/src"; dst="$t/2/dst"; mkdir -p "$src" "$dst"
echo a > "$dst/camgong-20261119-040000.sql.gz"; old "$dst/camgong-20261119-040000.sql.gz"
echo l1 > "$dst/withdraw-ledger.tsv"
pull "$src/" "$dst"
check "2 실패 알림" '[ $rc -ne 0 ] && [ "$(cat "$PINGS")" = https://hc.example/mac/fail ]'
check "2 7일 넘은 사본도 남음" '[ -f "$dst/camgong-20261119-040000.sql.gz" ]'
check "2 장부 남음" '[ "$(cat "$dst/withdraw-ledger.tsv")" = l1 ]'

# 3. 원천에 닿지 않는다(없는 경로) → 아무것도 안 지우고 실패 알림
dst="$t/3/dst"; mkdir -p "$dst"
echo a > "$dst/camgong-20261119-040000.sql.gz"; old "$dst/camgong-20261119-040000.sql.gz"
echo l1 > "$dst/withdraw-ledger.tsv"
pull "$t/3/missing/" "$dst"
check "3 실패" '[ $rc -ne 0 ]'
check "3 7일 넘은 사본도 남음" '[ -f "$dst/camgong-20261119-040000.sql.gz" ]'
check "3 실패 알림" '[ "$(cat "$PINGS")" = https://hc.example/mac/fail ]'

# 4. 서버 덤프가 실패하는 중이라 맥이 이미 지운 옛 덤프만 다시 온다 → 새 덤프가 아니라 지우지 않는다. 실패 알림
src="$t/4/src"; dst="$t/4/dst"; mkdir -p "$src" "$dst"
echo a > "$src/camgong-20261119-040000.sql.gz"; old "$src/camgong-20261119-040000.sql.gz"; echo l1 > "$src/withdraw-ledger.tsv"
echo b > "$dst/camgong-20261120-040000.sql.gz"; old "$dst/camgong-20261120-040000.sql.gz"
pull "$src/" "$dst"
check "4 실패 알림" '[ $rc -ne 0 ] && [ "$(cat "$PINGS")" = https://hc.example/mac/fail ]'
check "4 맥에 있던 옛 사본 남음" '[ -f "$dst/camgong-20261120-040000.sql.gz" ]'
check "4 옮긴 사본은 서버 덤프 시각을 지닌다(6일 규칙)" '[ -n "$(find "$dst/camgong-20261119-040000.sql.gz" -mmin +$((6 * 24 * 60)))" ]'

# 5. 장부가 한 번도 안 왔다 → 실패
src="$t/5/src"; dst="$t/5/dst"; mkdir -p "$src" "$dst"
echo new > "$src/camgong-20261126-040000.sql.gz"
pull "$src/" "$dst"
check "5 장부 없으면 실패" '[ $rc -ne 0 ] && [ "$(cat "$PINGS")" = https://hc.example/mac/fail ]'

# 5-1. 맥 디스크가 가득 차 옮기지 못한다 → 실패 알림(2일 안의 덤프가 이미 있어도). 덤프·장부 각각
printf '#!/bin/sh\ncase "$*" in *"$CP_FAIL"*) echo "No space left on device" >&2; exit 1;; esac\nexec /bin/cp "$@"\n' > "$t/bin/cp"; chmod +x "$t/bin/cp"
n=0
for what in camgong-20261126 withdraw-ledger; do
	n=$((n + 1)); src="$t/5a$n/src"; dst="$t/5a$n/dst"; mkdir -p "$src" "$dst"   # 경로에 $what 이 들어가지 않게
	echo new > "$src/camgong-20261126-040000.sql.gz"; echo l2 > "$src/withdraw-ledger.tsv"
	echo b > "$dst/camgong-20261125-040000.sql.gz"; echo l1 > "$dst/withdraw-ledger.tsv"
	CP_FAIL="$what" pull "$src/" "$dst"
	check "5-1 $what 옮기기 실패면 실패 알림" '[ $rc -ne 0 ] && [ "$(cat "$PINGS")" = https://hc.example/mac/fail ]'
done
rm "$t/bin/cp"

# 5-2. 서버가 먼 미래 이름의 덤프를 한 번 보냈다 → 그 뒤에도 새 덤프가 오면 지우기가 멈추지 않는다
src="$t/5b/src"; dst="$t/5b/dst"; mkdir -p "$src" "$dst"
echo junk > "$src/camgong-99991231-000000.sql.gz"; echo l > "$src/withdraw-ledger.tsv"
pull "$src/" "$dst"
rm "$src/camgong-99991231-000000.sql.gz"; echo new > "$src/camgong-20261126-040000.sql.gz"
echo a > "$dst/camgong-20261118-040000.sql.gz"; old "$dst/camgong-20261118-040000.sql.gz"
pull "$src/" "$dst"
check "5-2 성공" '[ $rc -eq 0 ]'
check "5-2 7일 넘은 사본 지움" '[ ! -e "$dst/camgong-20261118-040000.sql.gz" ]'

# 5-3. 수정 시각이 미래인 덤프 → 실패 알림
src="$t/5c/src"; dst="$t/5c/dst"; mkdir -p "$src" "$dst"
echo x > "$src/camgong-20261126-040000.sql.gz"; touch -t 203001010000 "$src/camgong-20261126-040000.sql.gz"; echo l > "$src/withdraw-ledger.tsv"
pull "$src/" "$dst"
check "5-3 미래 시각이면 실패 알림" '[ $rc -ne 0 ] && [ "$(cat "$PINGS")" = https://hc.example/mac/fail ]'

# 5-4. 가장 새 덤프가 3일 전이다(2일 기준) → 실패 알림
src="$t/5d/src"; dst="$t/5d/dst"; mkdir -p "$src" "$dst"
echo x > "$src/camgong-20261123-040000.sql.gz"; touch -t "$(date -v-3d +%Y%m%d%H%M)" "$src/camgong-20261123-040000.sql.gz"; echo l > "$src/withdraw-ledger.tsv"
pull "$src/" "$dst"
check "5-4 3일 된 덤프뿐이면 실패 알림" '[ $rc -ne 0 ] && [ "$(cat "$PINGS")" = https://hc.example/mac/fail ]'

# 6. backup.sh: 덤프 뒤 장부를 덮어써 둔다
b="$t/6"; mkdir -p "$b"
echo a > "$b/camgong-20261119-040000.sql.gz"; old "$b/camgong-20261119-040000.sql.gz"
echo old > "$b/withdraw-ledger.tsv"
printf '1\t2026-11-25T00:00:00Z\n' > "$t/ledger"
: > "$PINGS"
rc=0; BACKUP_DIR="$b" HC_PING_URL=https://hc.example/srv LEDGER="$t/ledger" bash "$here/backup.sh" > "$t/out" 2>&1 || rc=$?
check "6 성공" '[ $rc -eq 0 ]'
check "6 장부 복사" 'cmp -s "$t/ledger" "$b/withdraw-ledger.tsv"'
check "6 장부는 소유자만 읽음" '[ "$(stat -f %Lp "$b/withdraw-ledger.tsv")" = 600 ]'
check "6 옛 덤프 지움" '[ ! -e "$b/camgong-20261119-040000.sql.gz" ]'

# 7. backup.sh: 장부를 못 읽으면 실패한다. 새 덤프는 성공했으니 옛 덤프는 지우고, 전날 장부는 남긴다
b="$t/7"; mkdir -p "$b"
echo a > "$b/camgong-20261119-040000.sql.gz"; old "$b/camgong-20261119-040000.sql.gz"
echo old > "$b/withdraw-ledger.tsv"
: > "$PINGS"
rc=0; BACKUP_DIR="$b" HC_PING_URL=https://hc.example/srv LEDGER="$t/missing" bash "$here/backup.sh" > "$t/out" 2>&1 || rc=$?
check "7 실패" '[ $rc -ne 0 ]'
check "7 옛 덤프 지움" '[ ! -e "$b/camgong-20261119-040000.sql.gz" ]'
check "7 이전 장부 남음" '[ "$(cat "$b/withdraw-ledger.tsv")" = old ]'
check "7 .tmp 안 남음" '[ -z "$(find "$b" -name "*.tmp")" ]'
check "7 실패 알림" '[ "$(cat "$PINGS")" = https://hc.example/srv/fail ]'

[ "$fails" -eq 0 ] && echo "all passed" || { echo "$fails failed"; exit 1; }
