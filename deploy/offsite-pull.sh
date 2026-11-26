#!/usr/bin/env bash
# 서버 밖 백업(014): 운영자 맥이 서버의 백업 디렉터리(덤프 + 탈퇴 장부)를 매일 당겨 온다. launchd 가 돌린다(app.camgong.offsite-pull.plist).
# 설정은 맥의 파일 하나에만 둔다(주소·키·알림 주소는 저장소에 넣지 않는다). 기본 위치 ~/.config/camgong-offsite.conf, CAMGONG_OFFSITE_CONF 로 바꾼다.
#   SOURCE=deploy@<서버>:          # 서버 authorized_keys 의 고정 명령이 백업 디렉터리를 읽기 전용으로 보낸다. 로컬 디렉터리도 된다(끝에 /)
#   DEST=$HOME/camgong-offsite     # Time Machine 에서 뺀다
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

keep=$((6 * 24 * 60))   # 분. backup.sh 와 같은 기준

# 받는 곳(.incoming)은 서버 디렉터리의 거울이고, 보관하는 곳(DEST)은 거울을 따라 하지 않는다(014 결정 4).
# 서버가 덤프를 지우거나 비우거나 같은 이름을 망가진 내용으로 바꿔도, DEST 에 이미 있는 덤프는 지워지지도 덮어써지지도 않는다.
in="$DEST/.incoming"
mkdir -p "$in"
chmod 700 "$DEST" "$in"
# 지난번에 도중에 멈춘 것(rsync 임시 파일은 --exclude 에 걸려 --delete 가 지우지 않는다)
rm -f "$in"/.camgong-* "$in"/.withdraw-ledger.tsv.* "$DEST"/.camgong-*.tmp "$DEST/.withdraw-ledger.tsv.tmp"

# 옵션을 바꾸면 서버 authorized_keys 의 고정 명령도 같이 바꾼다(맥 rsync 가 보내는 명령과 같아야 한다, 확인은 -e "ssh -v").
# -a 대신 -rt(권한·소유자는 아래 chmod 로 맞춘다). -t 는 수정 시각을 옮겨, 아래 6일 규칙이 서버의 덤프 시각을 따른다.
# 끝난 덤프와 장부만 받는다(.tmp 는 서버에서 쓰는 중이다). --delete 는 거울(.incoming)에만 건다.
# /usr/bin/rsync(openrsync)로 고정한다: 다른 rsync 는 다른 명령을 보내 서버 고정 명령과 어긋난다.
/usr/bin/rsync -rt --delete -e "ssh -i ${SSH_KEY:-$HOME/.ssh/camgong_offsite} -o IdentitiesOnly=yes -o BatchMode=yes" \
	--include='camgong-*.sql.gz' --include='withdraw-ledger.tsv' --exclude='*' "$SOURCE" "$in/"

# DEST 에 없는 덤프만 옮긴다(덮어쓰지 않는다). 장부는 매번 가장 새것으로 바꾼다. 임시 이름에 쓰고 옮겨, 도중에 멈춰도 반쪽 파일이 남지 않는다.
# cp·mv 를 따로 둔다: && 로 이으면 set -e 가 cp 실패(디스크 가득 등)를 놓쳐 성공 알림이 간다.
fresh=""
for f in "$in"/camgong-*.sql.gz; do
	[ -f "$f" ] || continue
	name="$(basename "$f")"
	[ -e "$DEST/$name" ] && continue
	cp -p "$f" "$DEST/.$name.tmp"
	mv "$DEST/.$name.tmp" "$DEST/$name"
	[ -z "$(find "$DEST/$name" -mmin +$keep)" ] && fresh="$name"
done
if [ -f "$in/withdraw-ledger.tsv" ]; then
	cp -p "$in/withdraw-ledger.tsv" "$DEST/.withdraw-ledger.tsv.tmp"
	mv "$DEST/.withdraw-ledger.tsv.tmp" "$DEST/withdraw-ledger.tsv"
fi
chmod -R go= "$DEST"   # openrsync 는 -p 없이 받으면 umask 를 따르지 않고 서버 권한(644 등)으로 만든다
# 장부가 한 번도 안 왔으면 복구할 수 없다. 조용히 넘어가지 않는다.
[ -f "$DEST/withdraw-ledger.tsv" ] || { echo "error: 탈퇴 장부가 없다" >&2; exit 1; }

# 이번 실행에서 6일이 안 된 덤프를 새로 옮겼을 때만 6일 지난 사본을 지운다. 이름이 아니라 수정 시각으로 본다(서버가 이상한 이름을 보내도
# 지우기가 멈추지 않게). 맥이 먼저 지운 옛 덤프를 서버가 아직 갖고 있으면(서버 덤프 실패 중) 다시 옮기지만, 6일이 지나 있어 지우는 조건이
# 되지 않고 다음 새 덤프 때 같이 지워진다. 그동안은 서버 쪽처럼 7일을 넘길 수 있다(아래 실패 알림으로 알아챈다).
if [ -n "$fresh" ]; then
	find "$DEST" -maxdepth 1 -name 'camgong-*.sql.gz' -mmin +$keep -delete
	echo "received $fresh"
fi

# 2일 안의 덤프가 없으면 실패로 알린다(서버 덤프가 멈춤, 서버 경로가 바뀜 등으로 사본이 낡아 가는데 초록으로 남지 않게). 아무것도 지우지 않았다.
[ -n "$(find "$DEST" -maxdepth 1 -name 'camgong-*.sql.gz' -mmin -$((2 * 24 * 60)))" ] || { echo "error: 2일 안의 덤프가 없다" >&2; exit 1; }
