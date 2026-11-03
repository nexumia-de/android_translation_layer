#!/bin/sh
# Headless check of de.nexumia.atl.Permissions (src/installer-service/permissions.c): a fake ATL
# app (bwrap with a /.flatpak-info) asks; GTK's access dialog answers. Run inside a display with
# its own session bus:  tools/headless-shot.sh -d 12 -w 5 -x "tap 270 227" -x "tap 90 227" -x "tap 270 227" -- flatpak/permissions-test.sh
# (Allow contacts, Deny Google services, Allow microphone). Writes into a temporary directory.
T=$(mktemp -d)
export FLATPAK_USER_DIR=$T/flatpak XDG_CONFIG_HOME=$T/config XDG_DATA_HOME=$T/data
id=de.nexumia.atl.org.example.test
mkdir -p $FLATPAK_USER_DIR/app/$id/current/active/files/share/atl $XDG_DATA_HOME
printf 'android.permission.INTERNET\nandroid.permission.READ_CONTACTS\nandroid.permission.RECORD_AUDIO\n' > $FLATPAK_USER_DIR/app/$id/current/active/files/share/atl/permissions
printf '[Application]\nname=%s\n' $id > $T/info
/usr/libexec/xdg-desktop-portal-gtk >$T/gtk.log 2>&1 &
${ATL_INSTALLER_SERVICE:-builddir/atl-installer-service} >$T/service.log 2>&1 &
sleep 2
call() { bwrap --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/bin /bin --symlink usr/sbin /sbin --ro-bind /etc /etc --bind /tmp /tmp --proc /proc --dev /dev --ro-bind $T/info /.flatpak-info --setenv DBUS_SESSION_BUS_ADDRESS "$DBUS_SESSION_BUS_ADDRESS" -- gdbus call --session --timeout 600 --dest de.nexumia.atl.Permissions --object-path /de/nexumia/atl/Permissions --method de.nexumia.atl.Permissions.$1 $2; }
{
echo "host Check: $(gdbus call --session --dest de.nexumia.atl.Permissions --object-path /de/nexumia/atl/Permissions --method de.nexumia.atl.Permissions.Check android.permission.READ_CONTACTS 2>&1)"
echo "Check contacts: $(call Check android.permission.READ_CONTACTS 2>&1)"
echo "Request SMS (not requested): $(call Request android.permission.READ_SMS 2>&1)"
echo "Check camera (not handled): $(call Check android.permission.CAMERA 2>&1)"
echo "Request contacts: $(call Request android.permission.READ_CONTACTS 2>&1)"
echo "Check contacts: $(call Check android.permission.READ_CONTACTS 2>&1)"
echo "Request google: $(call Request atl.google-services 2>&1)"
echo "Request mic: $(call Request android.permission.RECORD_AUDIO 2>&1)"
grep -c atl-microphone $XDG_CONFIG_HOME/wireplumber/wireplumber.conf.d/90-atl-microphone.conf
grep -A1 "matches" $XDG_CONFIG_HOME/wireplumber/wireplumber.conf.d/90-atl-microphone.conf
} 2>&1 | tee $T/result.txt; echo "results in $T/result.txt"
kill %1 %2; sleep 5
