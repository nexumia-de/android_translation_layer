#!/bin/sh
# atl-push-server: the push server (ntfy) that the user's UnifiedPush distributor (KUnifiedPush)
# uses for Android apps in ATL and for native apps alike (docs/push.md).
#
#   atl-push-server get        print the server URL in use
#   atl-push-server set URL    use the ntfy server at URL (http:// or https://)
#   atl-push-server reset      use the default, https://ntfy.sh
#
# A running distributor is changed over D-Bus (org.kde.kunifiedpush.Management.setPushProvider):
# it saves the setting itself and moves the apps' registrations to the new server, which a
# restart after editing the file would not. Otherwise the per-user file
# ~/.config/KDE/kunifiedpush-distributor.conf is written and read at the distributor's next start.
# Exit status: 0 done, 1 error, 2 usage.
set -eu

DEFAULT_URL=https://ntfy.sh
BUS_NAME=org.unifiedpush.Distributor.kde
OBJECT=/org/unifiedpush/Distributor
MGMT=org.kde.kunifiedpush.Management
USER_CONF=${XDG_CONFIG_HOME:-$HOME/.config}/KDE/kunifiedpush-distributor.conf

usage() {
	echo "usage: atl-push-server get | set URL | reset" >&2
	exit 2
}

running() {
	gdbus call --session -d org.freedesktop.DBus -o /org/freedesktop/DBus \
		-m org.freedesktop.DBus.NameHasOwner "$BUS_NAME" 2>/dev/null | grep -q true
}

# value of KEY in [GROUP] of an INI file, empty if missing
ini_get() {
	[ -r "$1" ] || return 0
	awk -v g="[$2]" -v k="$3" '
		/^\[/ { in_g = ($0 == g); next }
		in_g && index($0, k "=") == 1 { print substr($0, length(k) + 2); exit }' "$1"
}

# set KEY=VALUE in [GROUP], keeping everything else (the distributor stores its clients there too)
ini_set() {
	mkdir -p "$(dirname "$1")"
	[ -e "$1" ] || : >"$1"
	tmp=$(mktemp "$1.XXXXXX")
	awk -v g="[$2]" -v k="$3" -v v="$4" '
		/^\[/ { if (in_g && !done) { print k "=" v; done = 1 } in_g = ($0 == g); seen = seen || in_g; print; next }
		in_g && index($0, k "=") == 1 { if (!done) print k "=" v; done = 1; next }
		{ print }
		END { if (!done) { if (!seen) print "\n" g; print k "=" v } }' "$1" >"$tmp"
	mv "$tmp" "$1"
}

get_url() {
	if running; then
		# ({'Url': <'https://ntfy.sh'>},)
		url=$(gdbus call --session -d "$BUS_NAME" -o "$OBJECT" -m "$MGMT.pushProviderConfiguration" Ntfy 2>/dev/null |
			sed -n "s/.*'Url': <'\([^']*\)'>.*/\1/p")
		[ -n "$url" ] && { echo "$url"; return; }
	fi
	url=$(ini_get "$USER_CONF" Ntfy Url)
	[ -n "$url" ] || url=$(ini_get /etc/xdg/KDE/kunifiedpush-distributor.conf Ntfy Url)
	echo "${url:-$DEFAULT_URL}"
}

set_url() {
	case $1 in
	http://?* | https://?*) ;;
	*) echo "atl-push-server: not an http(s) URL: $1" >&2; exit 1 ;;
	esac
	case $1 in *"'"* | *'"'* | *' '* | *'\'*) echo "atl-push-server: invalid URL: $1" >&2; exit 1 ;; esac
	if running; then
		gdbus call --session -d "$BUS_NAME" -o "$OBJECT" -m "$MGMT.setPushProvider" Ntfy "{'Url': <'$1'>}" >/dev/null
	else
		ini_set "$USER_CONF" PushProvider Type Ntfy
		ini_set "$USER_CONF" Ntfy Url "$1"
	fi
}

[ $# -ge 1 ] || usage
case $1 in
get) [ $# -eq 1 ] || usage; get_url ;;
set) [ $# -eq 2 ] || usage; set_url "$2" ;;
reset) [ $# -eq 1 ] || usage; set_url "$DEFAULT_URL" ;;
*) usage ;;
esac
