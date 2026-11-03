#!/bin/sh
# Headless check of de.nexumia.atl.Permissions (src/installer-service/permissions.c) and of the
# installer service's caller check: a fake ATL app (bwrap with a /.flatpak-info) asks; GTK's access
# dialog answers. Run inside a display with its own session bus:
#   tools/headless-shot.sh -d 14 -w 5 -x "tap 270 227" -x "tap 90 227" -x "tap 90 227" -x "tap 270 227" -x "tap 270 227" -- flatpak/permissions-test.sh
# (Allow contacts once for two requests, Deny Google services twice, Allow microphone, Allow
# background location). Writes into a
# temporary directory; exit status 0 when every check is ok.
# With the shared lock use flock -o, so the daemons started here cannot inherit and keep it;
# they are stopped when the script ends, also when it is interrupted.
T=$(mktemp -d)
export FLATPAK_USER_DIR=$T/flatpak XDG_CONFIG_HOME=$T/config XDG_DATA_HOME=$T/data
id=de.nexumia.atl.org.example.test
mkdir -p $FLATPAK_USER_DIR/app/$id/current/active/files/share/atl $XDG_DATA_HOME
printf 'android.permission.INTERNET\nandroid.permission.READ_CONTACTS\nandroid.permission.RECORD_AUDIO\nandroid.permission.ACCESS_BACKGROUND_LOCATION\n' > $FLATPAK_USER_DIR/app/$id/current/active/files/share/atl/permissions
printf '[Application]\nname=%s\n' $id > $T/info
printf '[Application]\nname=%s\n[Session Bus Policy]\nde.nexumia.atl.Installer=talk\n' de.nexumia.atl.org.example.store > $T/store-info
# its own permission store in $XDG_DATA_HOME, not the user's
/usr/libexec/xdg-permission-store --replace >$T/store.log 2>&1 &
store=$!
sleep 1
/usr/libexec/xdg-desktop-portal-gtk >$T/gtk.log 2>&1 &
gtk=$!
${ATL_PERMISSION_SERVICE:-builddir/atl-permission-service} >$T/service.log 2>&1 &
service=$!
ATL_BINARY=/bin/false ${ATL_INSTALLER_SERVICE:-builddir/atl-installer-service} >$T/installer.log 2>&1 &
installer=$!
trap 'kill $store $gtk $service $installer 2>/dev/null; wait 2>/dev/null' EXIT
trap 'exit 1' INT TERM HUP
sleep 2
sandboxed() { info=$1; shift; bwrap --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/bin /bin --symlink usr/sbin /sbin --ro-bind /etc /etc --bind /tmp /tmp --proc /proc --dev /dev --ro-bind $info /.flatpak-info --setenv DBUS_SESSION_BUS_ADDRESS "$DBUS_SESSION_BUS_ADDRESS" -- "$@"; }
call() { sandboxed $T/info gdbus call --session --timeout 600 --dest de.nexumia.atl.Permissions --object-path /de/nexumia/atl/Permissions --method de.nexumia.atl.Permissions.$1 $2 2>&1; }
installer_list() { sandboxed $1 gdbus call --session --dest $2 --object-path /de/nexumia/atl/Installer --method de.nexumia.atl.Installer.List 2>&1; }
fail=0
check() { # what, expected substring, actual
	case $3 in *"$2"*) r=ok ;; *) r=FAIL; fail=1 ;; esac
	echo "$r: $1: $3"
}
{
check "host caller refused" AccessDenied "$(gdbus call --session --dest de.nexumia.atl.Permissions --object-path /de/nexumia/atl/Permissions --method de.nexumia.atl.Permissions.Check android.permission.READ_CONTACTS 2>&1)"
check "check before asking" "'ask'" "$(call Check android.permission.READ_CONTACTS)"
check "not requested by the APK" "'no'" "$(call Request android.permission.READ_SMS)"
check "not decided here" InvalidArgs "$(call Check android.permission.CAMERA)"
# two requests at once: one dialog, both get its answer (Allow)
call Request android.permission.READ_CONTACTS > $T/r1 & p1=$!
sleep 1
call Request android.permission.READ_CONTACTS > $T/r2 & p2=$!
wait $p1 $p2
check "first of two concurrent requests" "'yes'" "$(cat $T/r1)"
check "second of two concurrent requests" "'yes'" "$(cat $T/r2)"
check "stored" "'yes'" "$(call Check android.permission.READ_CONTACTS)"
check "Google services, first no" "'no'" "$(call Request atl.google-services)"
check "Google services, second no" "'no'" "$(call Request atl.google-services)"
start=$(date +%s)
check "Google services, not asked a third time" "'no'" "$(call Request atl.google-services)"
check "  ...without a dialog" "fast" "$([ $(($(date +%s) - start)) -lt 3 ] && echo fast || echo slow)"
check "microphone allowed" "'yes'" "$(call Request android.permission.RECORD_AUDIO)"
check "  ...normal pulse socket as override" "pulseaudio" "$(cat $FLATPAK_USER_DIR/overrides/$id 2>&1)"
start=$(date +%s)
check "background location without foreground location" "'no'" "$(call Request android.permission.ACCESS_BACKGROUND_LOCATION)"
check "  ...without a dialog" "fast" "$([ $(($(date +%s) - start)) -lt 3 ] && echo fast || echo slow)"
# what the location portal stores after "allow"
gdbus call --session --dest org.freedesktop.impl.portal.PermissionStore --object-path /org/freedesktop/impl/portal/PermissionStore \
	--method org.freedesktop.impl.portal.PermissionStore.SetPermission location true location $id "['EXACT', '0']" >/dev/null
check "background location with foreground location (Allow)" "'yes'" "$(call Request android.permission.ACCESS_BACKGROUND_LOCATION)"
# the installer service: an app without access must not reach it, also not through the unique name
unique=$(gdbus call --session --dest org.freedesktop.DBus --object-path /org/freedesktop/DBus --method org.freedesktop.DBus.GetNameOwner de.nexumia.atl.Installer | sed "s/[(',)]//g")
check "installer refuses an app without access" AccessDenied "$(installer_list $T/info de.nexumia.atl.Installer)"
check "  ...also by unique name" AccessDenied "$(installer_list $T/info $unique)"
check "installer serves an app store" "@a(sh)" "$(installer_list $T/store-info de.nexumia.atl.Installer)"
} 2>&1 | tee $T/result.txt
echo "results in $T/result.txt"
sleep 5
! grep -q "^FAIL" $T/result.txt
