#!/usr/bin/env bash
# camgong DB 를 날짜별 파일로 덤프한다. 예: crontab 에 `0 4 * * * /path/to/deploy/backup.sh`
# 복구: gunzip -c <파일> | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot camgong'
set -euo pipefail
umask 077   # 덤프에 이메일·비밀번호 해시가 들어 있다. 서버의 다른 사용자가 못 읽게
cd "$(dirname "$0")"

dir="${BACKUP_DIR:-$HOME/camgong-backups}"
mkdir -p "$dir"
out="$dir/camgong-$(date +%Y%m%d-%H%M%S).sql.gz"
trap 'rm -f "$out.tmp"' EXIT

# 비밀번호는 컨테이너 안의 환경변수로 넘긴다(호스트 프로세스 목록에 남지 않게). 끝까지 성공한 덤프만 남긴다.
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers camgong' \
	| gzip > "$out.tmp"
mv "$out.tmp" "$out"
echo "$out"

# 7일이 지난 덤프를 지운다(010 결정 5-1: 탈퇴 전 값이 백업에 남는 기간). 새 덤프가 성공한 뒤에만 지운다(set -e).
find "$dir" -name 'camgong-*.sql.gz' -mtime +6 -delete

# TODO: 서버 밖(객체 저장소 등)으로 보낸다. 서버가 사라지면 이 디렉터리도 같이 사라진다. 밖의 사본에도 같은 7일 규칙을 건다.
