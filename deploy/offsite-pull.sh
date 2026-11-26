#!/usr/bin/env bash
# 서버 밖 백업(014): 운영자 맥이 서버의 백업 디렉터리(덤프 + 탈퇴 장부)를 매일 당겨 온다. launchd 가 돌린다(app.camgong.offsite-pull.plist).
# 설정은 맥의 파일 하나에만 둔다(주소·키·알림 주소는 저장소에 넣지 않는다). 기본 위치 ~/.config/camgong-offsite.conf, CAMGONG_OFFSITE_CONF 로 바꾼다.
#   SOURCE=deploy@<서버>:          # 서버 authorized_keys 의 rrsync -ro 가 백업 디렉터리로 묶는다. 로컬 디렉터리도 된다(끝에 /)
#   DEST=$HOME/camgong-offsite     # 권한 700, Time Machine 에서 뺀다
#   SSH_KEY=$HOME/.ssh/camgong_offsite
#   HC_PING_URL=https://hc-ping.com/<맥용 확인>
set -euo pipefail
umask 077   # 덤프에 이메일·비밀번호 해시가 들어 있다

conf="${CAMGONG_OFFSITE_CONF:-$HOME/.config/camgong-offsite.conf}"
# shellcheck source=/dev/null
. "$conf"
: "${SOURCE:?}" "${DEST:?}"

# 결과 알림(014 결정 5): backup.sh 와 같다. 끝까지 성공하면 확인 주소, 어디서든 실패하면 /fail.
hc="${HC_PING_URL:-}"
[ -n "$hc" ] || echo "warning: HC_PING_URL 이 비어 있어 결과를 알리지 않는다" >&2
hc_ping() {
	[ -n "$hc" ] || return 0
	curl -fsS -m 10 --retry 3 -o /dev/null "$hc$1" || echo "warning: 결과 알림 실패${1:+ ($1)}" >&2   # 주소는 비밀이라 찍지 않는다
}
finish() {
	status=$?
	if [ "$status" -eq 0 ]; then hc_ping ""; else hc_ping "/fail"; fi
	exit "$status"
}
trap finish EXIT

# 이름에 덤프 시각이 들어 있어(camgong-YYYYMMDD-HHMMSS) 정렬하면 가장 새 덤프가 마지막에 온다.
newest() { find "$DEST" -maxdepth 1 -name 'camgong-*.sql.gz' -exec basename {} \; | sort | tail -n 1; }

mkdir -p "$DEST"
before="$(newest)"

# --delete 를 쓰지 않는다: 서버에서 지워지거나 서버 디렉터리가 비어도 맥 사본은 남는다(014 결정 4).
# 끝난 덤프와 장부만 받는다(.tmp 는 서버에서 쓰는 중이다). -a 가 수정 시각을 옮겨, 아래 6일 규칙이 서버의 덤프 시각을 따른다.
# -c: 크기·시각이 같아도 내용으로 비교한다(장부가 같은 크기로 다시 쓰이면 건너뛰지 않게).
rsync -a -c -e "ssh -i ${SSH_KEY:-$HOME/.ssh/camgong_offsite} -o IdentitiesOnly=yes -o BatchMode=yes" \
	--include='camgong-*.sql.gz' --include='withdraw-ledger.tsv' --exclude='*' "$SOURCE" "$DEST/"

# 장부가 한 번도 안 왔으면 복구할 수 없다. 조용히 넘어가지 않는다.
[ -f "$DEST/withdraw-ledger.tsv" ] || { echo "error: 탈퇴 장부가 없다" >&2; exit 1; }

# 이번 실행에서 더 새 덤프를 받았을 때만 6일(분 단위) 지난 사본을 지운다(backup.sh 와 같은 기준).
# 맥이 먼저 지운 덤프를 서버가 아직 갖고 있으면(서버 덤프 실패 중) 다시 받는다. 가장 새 덤프는 그대로라 지우는 조건이 되지 않고,
# 다음 새 덤프 때 같이 지워진다. 그동안은 서버 쪽처럼 7일을 넘길 수 있다(알림으로 알아챈다).
after="$(newest)"
if [ -n "$after" ] && [[ "$after" > "$before" ]]; then
	find "$DEST" -maxdepth 1 -name 'camgong-*.sql.gz' -mmin +$((6 * 24 * 60)) -delete
	echo "received $after"
else
	echo "warning: 새 덤프가 없어 아무것도 지우지 않았다" >&2
fi
