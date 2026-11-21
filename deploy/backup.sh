#!/usr/bin/env bash
# camgong DB 를 날짜별 파일로 덤프한다. 예: crontab 에 `0 4 * * * /path/to/deploy/backup.sh`
# 복구: gunzip -c <파일> | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot camgong'
set -euo pipefail
umask 077   # 덤프에 이메일·비밀번호 해시가 들어 있다. 서버의 다른 사용자가 못 읽게
cd "$(dirname "$0")"

dir="${BACKUP_DIR:-$HOME/camgong-backups}"
out="$dir/camgong-$(date +%Y%m%d-%H%M%S).sql.gz"

# 결과 알림(010 결정 5-3): 끝까지 성공하면 healthchecks.io 확인 주소, 어디서든 실패하면 /fail 을 부른다. 하루 동안 아무 호출이 없어도
# (cron 이 안 돎, 서버가 죽음) healthchecks.io 가 메일을 보낸다. 주소는 .env 의 HC_PING_URL 에만 둔다. .env 를 source 하지 않고 그 줄만 읽는다.
hc="${HC_PING_URL:-}"
if [ -z "$hc" ] && [ -f .env ]; then
	# compose 의 env_file 이 받는 따옴표("…", '…')와 윈도 줄 끝(CR)을 걷어 낸다.
	hc="$(sed -n 's/^HC_PING_URL=//p' .env | tail -n 1 | tr -d '\r' | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/")"
fi
if [ -z "$hc" ]; then
	echo "warning: HC_PING_URL 이 비어 있어 백업 결과를 알리지 않는다" >&2
fi

# 알림이 실패해도 백업 결과(종료 코드)는 바뀌지 않는다.
hc_ping() {
	[ -n "$hc" ] || return 0
	curl -fsS -m 10 --retry 3 -o /dev/null "$hc$1" || echo "warning: 백업 결과 알림 실패${1:+ ($1)}" >&2   # 주소는 비밀이라 찍지 않는다
}

finish() {
	status=$?
	rm -f "$out.tmp"
	if [ "$status" -eq 0 ]; then hc_ping ""; else hc_ping "/fail"; fi
	exit "$status"
}
trap finish EXIT

mkdir -p "$dir"

# 비밀번호는 컨테이너 안의 환경변수로 넘긴다(호스트 프로세스 목록에 남지 않게). 끝까지 성공한 덤프만 남긴다.
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers camgong' \
	| gzip > "$out.tmp"
mv "$out.tmp" "$out"
echo "$out"

# 6일(분 단위)이 지난 덤프를 지운다. 매일 돌면 어떤 덤프도 7일을 넘기지 않는다(010 결정 5-1: 동의문의 "7일 안에").
# -mtime +6 은 7일이 꽉 찬 파일만 골라, 덤프 시간이 조금만 흔들려도 하루 더(8일) 남는다.
# 패턴 끝의 * 는 강제 종료(SIGKILL)로 trap 이 못 지운 .tmp 도 함께 지우려는 것이다.
# 새 덤프가 성공한 뒤에만 지운다(set -e): 덤프가 며칠 실패하면 남은 백업까지 지우지 않는다. 그동안은 7일을 넘길 수 있다.
find "$dir" -name 'camgong-*.sql.gz*' -mmin +$((6 * 24 * 60)) -delete

# TODO: 서버 밖(객체 저장소 등)으로 보낸다. 서버가 사라지면 이 디렉터리도 같이 사라진다. 밖의 사본에도 같은 7일 규칙을 건다.
