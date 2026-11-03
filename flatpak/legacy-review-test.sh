#!/bin/sh
# Headless check of the legacy permission review (Review in src/installer-service/permissions.c):
# a fake ATL app (bwrap with a /.flatpak-info) made for old Android asks for its review; GTK's
# access dialog shows it. Run inside a display with its own session bus:
#   tools/headless-shot.sh -d 10 -w 4 -x "tap 125 171" -x "tap 270 350" -x "sleep 8" -- flatpak/legacy-review-test.sh
# (tick Microphone, Continue)
# Exit status 0 when every check is ok. Use flock -o with the shared lock; the daemons started here
# are stopped when the script ends.
T=$(mktemp -d)
export FLATPAK_USER_DIR=$T/flatpak XDG_CONFIG_HOME=$T/config XDG_DATA_HOME=$T/data
id=de.nexumia.atl.org.example.legacy
modern=de.nexumia.atl.org.example.modern
for app in $id $modern; do
	mkdir -p $FLATPAK_USER_DIR/app/$app/current/active/files/share/atl
	printf 'android.permission.INTERNET\nandroid.permission.ACCESS_FINE_LOCATION\nandroid.permission.RECORD_AUDIO\nandroid.permission.READ_CONTACTS\nandroid.permission.READ_CALENDAR\n' > $FLATPAK_USER_DIR/app/$app/current/active/files/share/atl/permissions
done
echo 21 > $FLATPAK_USER_DIR/app/$id/current/active/files/share/atl/target-sdk
echo 30 > $FLATPAK_USER_DIR/app/$modern/current/active/files/share/atl/target-sdk
mkdir -p $XDG_DATA_HOME
printf '[Application]\nname=%s\n' $id > $T/info
printf '[Application]\nname=%s\n' $modern > $T/modern-info
/usr/libexec/xdg-permission-store --replace >$T/store.log 2>&1 &
store=$!
sleep 1
/usr/libexec/xdg-desktop-portal-gtk >$T/gtk.log 2>&1 &
gtk=$!
${ATL_PERMISSION_SERVICE:-builddir/atl-permission-service} >$T/service.log 2>&1 &
service=$!
trap 'kill $store $gtk $service 2>/dev/null; wait 2>/dev/null' EXIT
trap 'exit 1' INT TERM HUP
sleep 2
set_answer() { gdbus call --session --dest org.freedesktop.impl.portal.PermissionStore --object-path /org/freedesktop/impl/portal/PermissionStore \
	--method org.freedesktop.impl.portal.PermissionStore.SetPermission de.nexumia.atl true $1 $id "$2" >/dev/null; }
# earlier answers: contacts yes (shown ticked), calendar finally no (left out)
set_answer android.permission.READ_CONTACTS "['yes']"
set_answer android.permission.READ_CALENDAR "['no', '2']"
call() { info=$1; shift; bwrap --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/bin /bin --symlink usr/sbin /sbin --ro-bind /etc /etc --bind /tmp /tmp --proc /proc --dev /dev --ro-bind $info /.flatpak-info --setenv DBUS_SESSION_BUS_ADDRESS "$DBUS_SESSION_BUS_ADDRESS" -- gdbus call --session --timeout 600 --dest de.nexumia.atl.Permissions --object-path /de/nexumia/atl/Permissions --method de.nexumia.atl.Permissions.$1 $2 2>&1; }
fail=0
check() { case $3 in *"$2"*) r=ok ;; *) r=FAIL; fail=1 ;; esac; echo "$r: $1: $3"; }
{
check "an app made for Android 6+ gets no review" AccessDenied "$(call $T/modern-info Review)"
# two calls at once: one dialog, both get its answers
call $T/info Review > $T/r1 & p1=$!
sleep 1
call $T/info Review > $T/r2 & p2=$!
wait $p1 $p2
review=$(cat $T/r1)
check "microphone ticked" "'android.permission.RECORD_AUDIO': 'yes'" "$review"
check "contacts kept (shown ticked)" "'android.permission.READ_CONTACTS': 'yes'" "$review"
check "calendar's final no left out" "absent" "$(echo "$review" | grep -q READ_CALENDAR && echo present || echo absent)"
check "location not reviewed (its portal asks)" "absent" "$(echo "$review" | grep -q LOCATION && echo present || echo absent)"
check "second concurrent call: same answers" "'android.permission.RECORD_AUDIO': 'yes'" "$(cat $T/r2)"
check "calendar still no" "'no'" "$(call $T/info Check android.permission.READ_CALENDAR)"
start=$(date +%s)
check "later review: no dialog" "'android.permission.RECORD_AUDIO': 'yes'" "$(call $T/info Review)"
check "  ...fast" fast "$([ $(($(date +%s) - start)) -lt 3 ] && echo fast || echo slow)"
} 2>&1 | tee $T/result.txt
echo "results in $T/result.txt"
sleep 3
! grep -q "^FAIL" $T/result.txt
