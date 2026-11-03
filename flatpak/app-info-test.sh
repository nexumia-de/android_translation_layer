#!/bin/sh
# Headless check of --app-info (src/main-executable/app_info.c) with a fake installed app in a
# temporary Flatpak user directory and its own permission store. Run inside a display with its own
# session bus, from the build directory:
#   APP_INFO_TEST_WAIT=25 tools/headless-shot.sh -d 10 -w 4 -x "tap 310 330" -x "tap 310 385" -- ../flatpak/app-info-test.sh
# then read the checks it prints. With the shared lock use flock -o; the daemons started here are
# stopped when the script ends.
T=$(mktemp -d)
export FLATPAK_USER_DIR=$T/flatpak XDG_DATA_HOME=$T/data XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR:-$T}
id=de.nexumia.atl.org.example.test
files=$FLATPAK_USER_DIR/app/$id/current/active/files/share/atl
mkdir -p "$files" "$FLATPAK_USER_DIR/exports/share/applications" "$XDG_DATA_HOME"
printf 'android.permission.INTERNET\nandroid.permission.RECORD_AUDIO\nandroid.permission.READ_CONTACTS\n' > "$files/permissions"
echo 1.2.3 > "$files/version"
printf '[Desktop Entry]\nType=Application\nName=Example App\nExec=true\n' > "$FLATPAK_USER_DIR/exports/share/applications/$id.desktop"
/usr/libexec/xdg-permission-store --replace >$T/store.log 2>&1 &
store=$!
trap 'kill $store $app 2>/dev/null; wait 2>/dev/null' EXIT
trap 'exit 1' INT TERM HUP
sleep 1
# an answer from the permission service's dialog, shown as a switch
gdbus call --session --dest org.freedesktop.impl.portal.PermissionStore --object-path /org/freedesktop/impl/portal/PermissionStore \
	--method org.freedesktop.impl.portal.PermissionStore.SetPermission de.nexumia.atl true android.permission.READ_CONTACTS $id "['yes']" >/dev/null
${ATL:-./android-translation-layer} --app-info org.example.test >$T/app.log 2>&1 &
app=$!
# the steps tap the switches meanwhile
sleep ${APP_INFO_TEST_WAIT:-40}
fail=0
check() { case $3 in *"$2"*) r=ok ;; *) r=FAIL; fail=1 ;; esac; echo "$r: $1: $3"; }
check "internet switched on" "network" "$(cat $FLATPAK_USER_DIR/overrides/$id 2>&1 | grep shared)"
check "microphone switched on" "'yes'" "$(gdbus call --session --dest org.freedesktop.impl.portal.PermissionStore --object-path /org/freedesktop/impl/portal/PermissionStore --method org.freedesktop.impl.portal.PermissionStore.GetPermission de.nexumia.atl android.permission.RECORD_AUDIO $id 2>&1)"
check "  ...normal pulse socket" "pulseaudio" "$(cat $FLATPAK_USER_DIR/overrides/$id 2>&1 | grep sockets)"
echo "results above; files in $T"
sleep 3
exit $fail
