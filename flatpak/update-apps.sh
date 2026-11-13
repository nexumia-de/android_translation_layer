#!/bin/sh
# Move the app Flatpaks make-app.sh generated onto the installed engine (the
# base app). Generated apps copy the engine's /app, so an engine update
# reaches them only this way (docs/hardening-roadmap.md, "An engine update
# regenerates the apps"). The app keeps its own files (APKs, launcher, icon,
# D-Bus service, permission list) and its Flatpak metadata, so its
# permissions and the user's overrides stay as they are; only the runtime
# follows the base app's. Nothing is compiled.
#
# Usage: flatpak/update-apps.sh [options] [APP_ID...]
#
#   -b BASE  base app with the ATL engine (default: de.nexumia.atl.BaseApp)
#   -r DIR   local repository (default: ~/.local/share/android_translation_layer/flatpak-repo)
#   -n       only print the apps that are not on the installed engine
#   -f       update the apps even if they are on the installed engine
#   -i       install (or refresh) the systemd user units that run this script
#            when the base app changes and at login, then exit
#
# Without APP_ID: every app from the remote atl-local with the id prefix
# de.nexumia.atl. An app is on the installed engine when its
# share/atl/engine holds the base app's commit; apps generated before
# that file existed are updated once.
#
# Installed as libexec/android-translation-layer/update-app-flatpaks.
set -eu

base=de.nexumia.atl.BaseApp
repo=$HOME/.local/share/android_translation_layer/flatpak-repo
dry_run= force= install_units=

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit "${1:-0}"; }

while getopts "b:r:nfih" opt; do
	case $opt in
	b) base=$OPTARG ;;
	r) repo=$OPTARG ;;
	n) dry_run=1 ;;
	f) force=1 ;;
	i) install_units=1 ;;
	h) usage 0 ;;
	*) usage 1 ;;
	esac
done
shift $((OPTIND - 1))

read_base() {
	base_dir=$(flatpak info --user --show-location "$base" 2>/dev/null || flatpak info --show-location "$base")
	base_commit=$(flatpak info --user --show-commit "$base" 2>/dev/null || flatpak info --show-commit "$base")
	runtime=$(flatpak info --show-runtime "$base")
}

# The path unit fires while Flatpak is still deploying: the location read
# then can be the old deployment, which Flatpak deletes a moment later.
# Wait until two reads two seconds apart agree on an existing deployment.
settle_base() {
	i=0
	read_base
	while [ $i -lt 30 ]; do
		prev=$base_dir
		sleep 2
		read_base
		[ "$base_dir" = "$prev" ] && [ -d "$base_dir/files" ] && return 0
		i=$((i + 1))
	done
	echo "$base: no stable deployment after 60 s" >&2
	return 1
}

read_base

# The path unit watches the directory of the base app's deployments: Flatpak
# replaces its "active" link there on every update.
if [ -n "$install_units" ]; then
	units_dir=
	for dir in "$(dirname "$0")/update-apps" "$(dirname "$0")/../../share/android-translation-layer/update-apps"; do
		[ -f "$dir/atl-update-apps.service" ] && { units_dir=$dir; break; }
	done
	[ -n "$units_dir" ] || { echo "update-apps/ not found" >&2; exit 1; }
	script=$(cd "$(dirname "$0")" && pwd)/$(basename "$0")
	config=${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user
	mkdir -p "$config"
	changed=
	for unit in atl-update-apps.service atl-update-apps.path; do
		sed -e "s|@SCRIPT@|$script|" -e "s|@BASE@|$base|" -e "s|@BASE_DEPLOY_DIR@|$(dirname "$base_dir")|" \
			"$units_dir/$unit" > "$config/$unit.new"
		if cmp -s "$config/$unit.new" "$config/$unit"; then
			rm "$config/$unit.new"
		else
			mv "$config/$unit.new" "$config/$unit"
			changed=1
		fi
	done
	[ -z "$changed" ] || systemctl --user daemon-reload 2>/dev/null || true
	systemctl --user enable atl-update-apps.service >/dev/null 2>&1 &&
		systemctl --user enable --now atl-update-apps.path >/dev/null 2>&1 ||
		echo "warning: the units for updating apps with the engine are not enabled" >&2
	exit 0
fi

# not "flatpak info": Flatpak 1.18.4 aborts in it for apps from atl-local (see make-app.sh)
user_dir=${FLATPAK_USER_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/flatpak}
if [ $# -eq 0 ]; then
	set -- $(flatpak list --user --app --columns=application,origin |
		awk -v base="$base" '$2 == "atl-local" && index($1, "de.nexumia.atl.") == 1 && $1 != base { print $1 }')
fi

# one run at a time: the path unit and a manual run may overlap
lock=${XDG_RUNTIME_DIR:-/tmp}/atl-update-apps.lock
exec 9>"$lock"
flock 9
settle_base || exit 1

status=0
for id in "$@"; do
	app_dir=$user_dir/app/$id/current/active
	if [ ! -f "$app_dir/files/bin/atl-app" ]; then
		echo "$id: not an app make-app.sh generated, skipped" >&2
		continue
	fi
	if [ -z "$force" ] && [ "$(cat "$app_dir/files/share/atl/engine" 2>/dev/null)" = "$base_commit" ]; then
		continue
	fi
	if [ -n "$dry_run" ]; then
		echo "$id"
		continue
	fi

	work=$(mktemp -d)
	build=$work/build
	mkdir -p "$build/files"
	# not -a: the deployment belongs to another group than the user may give files to
	if ! cp -dR "$base_dir/files/." "$build/files/" 2>/dev/null; then
		# the engine changed under us: copy the deployment that replaced it
		rm -rf "$build/files" && mkdir -p "$build/files"
		settle_base && cp -dR "$base_dir/files/." "$build/files/" || {
			echo "$id: update failed, the engine could not be copied" >&2
			status=1
			rm -rf "$work"
			continue
		}
	fi
	rm -f "$build/files/manifest.json"
	# the app's own files, as make-app.sh wrote them
	mkdir -p "$build/files/share/atl"
	for f in "$app_dir/files/share/atl/"*; do
		[ -f "$f" ] && cp "$f" "$build/files/share/atl/"
	done
	for f in bin/atl-app share/applications/$id.desktop share/dbus-1/services/$id.service; do
		mkdir -p "$(dirname "$build/files/$f")"
		cp "$app_dir/files/$f" "$build/files/$f"
	done
	for f in "$app_dir/files/share/icons/hicolor/"*/apps/"$id".*; do
		[ -f "$f" ] || continue
		rel=${f#"$app_dir/files/"}
		mkdir -p "$(dirname "$build/files/$rel")"
		cp "$f" "$build/files/$rel"
	done
	printf '%s\n' "$base_commit" > "$build/files/share/atl/engine"
	sed "s|^runtime=.*|runtime=$runtime|" "$app_dir/metadata" > "$build/metadata"

	# no options: the metadata already has the app's permissions, build-finish only exports
	# the launcher, icon and D-Bus service
	if flatpak build-finish "$build" >/dev/null &&
		flatpak build-export "$repo" "$build" stable >/dev/null &&
		flatpak build-update-repo "$repo" >/dev/null &&
		flatpak update --user -y --noninteractive "$id" >/dev/null; then
		echo "updated $id to engine ${base_commit%"${base_commit#????????????}"}"
	else
		echo "$id: update failed" >&2
		status=1
	fi
	rm -rf "$work"
done
exit $status
