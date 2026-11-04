#!/bin/sh
# Headless check of Bluetooth asked on first use (docs/bluetooth.md): a fake ATL app (bwrap with a
# /.flatpak-info) made before Android 12 (only android.permission.BLUETOOTH) asks for
# BLUETOOTH_CONNECT, as android.bluetooth.BluetoothAdapter does when BlueZ is out of reach. GTK's
# access dialog asks; Allow opens BlueZ in the app's override and restarts the app. Run inside a
# display with its own session bus:
#   tools/headless-shot.sh -d 10 -w 5 -x "tap 270 272" -- flatpak/bluetooth-test.sh
# Exit status 0 when every check is ok. With the shared lock use flock -o; the daemons started here
# are stopped when the script ends.
T=$(mktemp -d)
export FLATPAK_USER_DIR=$T/flatpak XDG_CONFIG_HOME=$T/config XDG_DATA_HOME=$T/data
id=de.nexumia.atl.org.example.legacybt
other=de.nexumia.atl.org.example.nobt
for app in $id $other; do
	mkdir -p $FLATPAK_USER_DIR/app/$app/current/active/files/share/atl
	printf '[Application]\nname=%s\n' $app > $T/$app.info
done
printf 'android.permission.BLUETOOTH\nandroid.permission.BLUETOOTH_ADMIN\n' > $FLATPAK_USER_DIR/app/$id/current/active/files/share/atl/permissions
printf 'android.permission.INTERNET\n' > $FLATPAK_USER_DIR/app/$other/current/active/files/share/atl/permissions
mkdir -p $XDG_DATA_HOME $T/bin
# flatpak as the service runs it: overrides for real, kill and run only written down
real=$(command -v flatpak)
printf '#!/bin/sh\necho "flatpak $*" >> %s/flatpak.log\n[ "$1" = override ] && exec %s "$@"\nexit 0\n' "$T" "$real" > $T/bin/flatpak
chmod +x $T/bin/flatpak
/usr/libexec/xdg-permission-store --replace >$T/store.log 2>&1 &
store=$!
sleep 1
/usr/libexec/xdg-desktop-portal-gtk >$T/gtk.log 2>&1 &
gtk=$!
(PATH=$T/bin:$PATH ${ATL_PERMISSION_SERVICE:-builddir/atl-permission-service}; echo "service ended: $?") >$T/service.log 2>&1 &
service=$!
trap 'kill $store $gtk $service 2>/dev/null; wait 2>/dev/null' EXIT
trap 'exit 1' INT TERM HUP
sleep 2
sandboxed() { info=$1; shift; bwrap --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/bin /bin --symlink usr/sbin /sbin --ro-bind /etc /etc --bind /tmp /tmp --proc /proc --dev /dev --ro-bind $info /.flatpak-info --setenv DBUS_SESSION_BUS_ADDRESS "$DBUS_SESSION_BUS_ADDRESS" -- "$@"; }
call() { sandboxed $T/$1.info gdbus call --session --timeout 600 --dest de.nexumia.atl.Permissions --object-path /de/nexumia/atl/Permissions --method de.nexumia.atl.Permissions.$2 $3 2>&1; }
check() { case $3 in *"$2"*) r=ok ;; *) r=FAIL ;; esac; echo "$r: $1: $3"; }
{
check "no Bluetooth permission: no" "'no'" "$(call $other Request android.permission.BLUETOOTH_CONNECT)"
check "before: no BlueZ" "absent" "$(cat $FLATPAK_USER_DIR/overrides/$id 2>/dev/null || echo absent)"
check "made before Android 12, allowed" "'yes'" "$(call $id Request android.permission.BLUETOOTH_CONNECT)"
check "  ...BlueZ in the override" "org.bluez=talk" "$(cat $FLATPAK_USER_DIR/overrides/$id 2>&1)"
sleep 5
check "  ...restarted" "flatpak run --user $id" "$(cat $T/flatpak.log 2>&1)"
check "  ...stored" "'yes'" "$(call $id Check android.permission.BLUETOOTH_CONNECT)"
start=$(date +%s)
check "asked again: yes without a dialog" "'yes'" "$(call $id Request android.permission.BLUETOOTH_CONNECT)"
check "  ...fast" fast "$([ $(($(date +%s) - start)) -lt 3 ] && echo fast || echo slow)"
check "  ...not restarted again" "1" "$(grep -c 'flatpak run' $T/flatpak.log)"
check "the service is still running (no crash after an answer)" "absent" "$(grep -a 'service ended' $T/service.log || echo absent)"
} 2>&1 | tee $T/result.txt
echo "results in $T/result.txt"
[ -n "${BLUETOOTH_TEST_RESULT:-}" ] && cp $T/result.txt "$BLUETOOTH_TEST_RESULT"
! grep -q "^FAIL" $T/result.txt
