#!/bin/sh
# Test atl-push-server on a private session bus with an own config directory, without and with a
# running kunifiedpush-distributor (needs gdbus and kunifiedpush). No network: the distributor gets
# unreachable local URLs. usage: src/push/atl-push-server-test.sh
[ -n "${ATL_PUSH_TEST_INNER:-}" ] || exec env ATL_PUSH_TEST_INNER=1 dbus-run-session -- "$0" "$@"
set -u
helper=$(dirname "$0")/atl-push-server.sh
XDG_CONFIG_HOME=$(mktemp -d); export XDG_CONFIG_HOME
conf=$XDG_CONFIG_HOME/KDE/kunifiedpush-distributor.conf
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fail=1; fi; }
trap 'kill "${KUP:-}" 2>/dev/null; rm -rf "$XDG_CONFIG_HOME"' EXIT INT TERM

# without a distributor: the per-user file
mkdir -p "$(dirname "$conf")"
printf '[Clients]\nabc=keep\n' >"$conf"
"$helper" set http://127.0.0.1:9/a
check "file: set" "$("$helper" get)" http://127.0.0.1:9/a
check "file: other groups kept" "$(grep -c '^abc=keep$' "$conf")" 1
check "file: provider type" "$(grep -c '^Type=Ntfy$' "$conf")" 1
"$helper" reset
check "file: reset" "$("$helper" get)" https://ntfy.sh
check "file: one Url line" "$(grep -c '^Url=' "$conf")" 1
"$helper" set 'http://x/ y' 2>/dev/null; check "file: bad URL refused" $? 1
"$helper" set ftp://x 2>/dev/null; check "file: non-http refused" $? 1
"$helper" frob 2>/dev/null; check "usage" $? 2

# with a running distributor: over D-Bus
if command -v kunifiedpush-distributor >/dev/null; then
	"$helper" set http://127.0.0.1:9/b
	QT_QPA_PLATFORM=offscreen kunifiedpush-distributor >/dev/null 2>&1 & KUP=$!
	for _ in 1 2 3 4 5 6 7 8 9 10; do
		gdbus call --session -d org.freedesktop.DBus -o /org/freedesktop/DBus -m org.freedesktop.DBus.NameHasOwner org.unifiedpush.Distributor.kde | grep -q true && break
		sleep 1
	done
	check "dbus: reads the running setting" "$("$helper" get)" http://127.0.0.1:9/b
	"$helper" set http://127.0.0.1:9/c
	check "dbus: set" "$("$helper" get)" http://127.0.0.1:9/c
	sleep 1
	check "dbus: distributor saved it" "$(grep '^Url=' "$conf")" Url=http://127.0.0.1:9/c
else
	echo "skip D-Bus part: kunifiedpush-distributor not installed"
fi
exit $fail
