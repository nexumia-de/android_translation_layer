#!/bin/sh
# Headless check of the legacy permission review (Review in src/installer-service/permissions.c):
# a fake ATL app (bwrap with a /.flatpak-info) made for old Android asks for its review; GTK's
# access dialog shows it. Run inside a display with its own session bus:
#   tools/headless-shot.sh -d 10 -w 4 -x "tap 125 215" -x "tap 270 394" -x "sleep 8" -- flatpak/legacy-review-test.sh
# (tick Camera, Continue)
# Exit status 0 when every check is ok. Use flock -o with the shared lock; the daemons started here
# are stopped when the script ends.
T=$(mktemp -d)
export FLATPAK_USER_DIR=$T/flatpak XDG_CONFIG_HOME=$T/config XDG_DATA_HOME=$T/data
id=de.nexumia.atl.org.example.legacy
mkdir -p $FLATPAK_USER_DIR/app/$id/current/active/files/share/atl $XDG_DATA_HOME
printf 'android.permission.INTERNET\nandroid.permission.ACCESS_FINE_LOCATION\nandroid.permission.CAMERA\nandroid.permission.READ_CONTACTS\n' > $FLATPAK_USER_DIR/app/$id/current/active/files/share/atl/permissions
printf '[Application]\nname=%s\n' $id > $T/info
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
call() { bwrap --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/bin /bin --symlink usr/sbin /sbin --ro-bind /etc /etc --bind /tmp /tmp --proc /proc --dev /dev --ro-bind $T/info /.flatpak-info --setenv DBUS_SESSION_BUS_ADDRESS "$DBUS_SESSION_BUS_ADDRESS" -- gdbus call --session --timeout 600 --dest de.nexumia.atl.Permissions --object-path /de/nexumia/atl/Permissions --method de.nexumia.atl.Permissions.$1 $2 2>&1; }
fail=0
check() { case $3 in *"$2"*) r=ok ;; *) r=FAIL; fail=1 ;; esac; echo "$r: $1: $3"; }
{
review=$(call Review)
check "review: camera allowed" "'android.permission.CAMERA': 'yes'" "$review"
check "review: location off by default" "'android.permission.ACCESS_FINE_LOCATION': 'no'" "$review"
check "review: contacts off by default" "'android.permission.READ_CONTACTS': 'no'" "$review"
check "review: internet is not part of it" "absent" "$(echo "$review" | grep -q INTERNET && echo present || echo absent)"
check "contacts stored as no" "'no'" "$(call Check android.permission.READ_CONTACTS)"
start=$(date +%s)
check "second review: same answers" "'android.permission.CAMERA': 'yes'" "$(call Review)"
check "  ...without a dialog" fast "$([ $(($(date +%s) - start)) -lt 3 ] && echo fast || echo slow)"
} 2>&1 | tee $T/result.txt
echo "results in $T/result.txt"
sleep 3
! grep -q "^FAIL" $T/result.txt
