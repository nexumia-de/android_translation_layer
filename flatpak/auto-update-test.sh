#!/bin/sh
# Headless check of updates without asking (docs/installing-apps.md, "Updates without asking"): a
# fake app store (bwrap with a /.flatpak-info) hands APKs to the installer service with and without
# the unattended option; --install-ask from the build directory decides; a fake make-app-flatpak
# records what would be installed. Run inside a display with its own session bus:
#   AUTO_UPDATE_RESULT=/tmp/r.txt tools/headless-shot.sh -d 1 -x 'until [ -e /tmp/r.txt ]; do sleep 5; done' \
#     -- flatpak/auto-update-test.sh OLD.apk NEW.apk PACKAGE PERMISSIONS
# OLD.apk and NEW.apk: the same app and signer, NEW with a higher versionCode (docs/installing-apps.md
# says how to make them); PERMISSIONS: the ';'-separated <uses-permission> list of both. Exit status 0 when every check is ok. Use flock -o with the shared lock.
set -u
old=$(realpath "$1") new=$(realpath "$2")
src=$(realpath "$(dirname "$0")/..")
T=$(mktemp -d)
export FLATPAK_USER_DIR=$T/flatpak XDG_CONFIG_HOME=$T/config XDG_DATA_HOME=$T/data XDG_CACHE_HOME=$T/cache
mkdir -p $XDG_DATA_HOME $XDG_CONFIG_HOME $XDG_CACHE_HOME
store=org.example.store
pkg=$3 perms=$4
atl=$FLATPAK_USER_DIR/app/de.nexumia.atl.$pkg/current/active/files/share/atl

# make-app-flatpak's options; it "installs" by writing the files the host reads
cat > $T/make-app <<'EOF'
#!/bin/sh
echo "make-app $*" >> "$MAKE_APP_LOG"
installer= permissions= target= version=
while getopts "p:n:i:b:a:m:P:N:V:f:r:hT:I:" opt; do
	case $opt in p) pkg=$OPTARG ;; P) permissions=$OPTARG ;; T) target=$OPTARG ;; V) version=$OPTARG ;; I) installer=$OPTARG ;; esac
done
shift $((OPTIND - 1))
dir=$FLATPAK_USER_DIR/app/de.nexumia.atl.$pkg/current/active/files/share/atl
mkdir -p $dir
cp "$1" $dir/apk0.apk
printf '%s' "$permissions" | tr ';' '\n' > $dir/permissions
echo "$target" > $dir/target-sdk
echo "$version" > $dir/version
echo "$installer" > $dir/installer
EOF
chmod 755 $T/make-app
export ATL_MAKE_FLATPAK=$T/make-app MAKE_APP_LOG=$T/make-app.log
cat > $T/atl <<EOF
#!/bin/sh
cd "$src/builddir" && RUN_FROM_BUILDDIR=1 LD_LIBRARY_PATH=\$HOME/.local/lib/art:\${LD_LIBRARY_PATH:-} exec ./android-translation-layer "\$@"
EOF
chmod 755 $T/atl

# the installed app as a store installed it: the old APK and what make-app-flatpak wrote
seed() { # installer permissions target
	mkdir -p $atl
	cp "$old" $atl/apk0.apk
	echo "$1" > $atl/installer
	printf '%s' "$2" | tr ';' '\n' > $atl/permissions
	echo "$3" > $atl/target-sdk
}

printf '[Application]\nname=de.nexumia.atl.%s\n[Session Bus Policy]\nde.nexumia.atl.Installer=talk\n' $store > $T/store-info
cat > $T/client.py <<'EOF'
import sys, os
from gi.repository import Gio, GLib
bus = Gio.bus_get_sync(Gio.BusType.SESSION)
if sys.argv[1] == "get":
    r = bus.call_sync("de.nexumia.atl.Installer", "/de/nexumia/atl/Installer", "de.nexumia.atl.Installer", "GetInstaller",
                      GLib.Variant("(s)", (sys.argv[2],)), GLib.VariantType("(s)"), 0, -1, None)
    print("installer=" + r.unpack()[0])
    sys.exit()
fds = Gio.UnixFDList.new()
fds.append(os.open(sys.argv[1], os.O_RDONLY))
options = {"sdk-int": GLib.Variant("s", "26")}
if len(sys.argv) > 2:
    options["unattended"] = GLib.Variant("b", True)
r, _ = bus.call_with_unix_fd_list_sync("de.nexumia.atl.Installer", "/de/nexumia/atl/Installer", "de.nexumia.atl.Installer", "Install",
                                       GLib.Variant("(aha{sv})", ([0], options)), GLib.VariantType("(s)"), 0, 600000, fds, None)
print("error=" + r.unpack()[0])
EOF
sandboxed() { bwrap --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/bin /bin --symlink usr/sbin /sbin --ro-bind /etc /etc --bind /tmp /tmp --ro-bind "$(dirname "$new")" "$(dirname "$new")" --proc /proc --dev /dev --ro-bind $T/store-info /.flatpak-info --setenv DBUS_SESSION_BUS_ADDRESS "$DBUS_SESSION_BUS_ADDRESS" -- "$@"; }

/usr/libexec/xdg-permission-store --replace >$T/store.log 2>&1 &
permission_store=$!
# a notification server that writes down what it is sent
cat > $T/notifications.py <<'EOF'
import sys
from gi.repository import Gio, GLib
xml = """<node><interface name="org.freedesktop.Notifications"><method name="Notify">
<arg type="s" direction="in"/><arg type="u" direction="in"/><arg type="s" direction="in"/><arg type="s" direction="in"/>
<arg type="s" direction="in"/><arg type="as" direction="in"/><arg type="a{sv}" direction="in"/><arg type="i" direction="in"/>
<arg type="u" direction="out"/></method></interface></node>"""
def call(conn, sender, path, iface, method, params, invocation):
    print("notify:", params.unpack()[3], "|", params.unpack()[4], flush=True)
    invocation.return_value(GLib.Variant("(u)", (1,)))
def acquired(conn, name):
    conn.register_object("/org/freedesktop/Notifications", Gio.DBusNodeInfo.new_for_xml(xml).interfaces[0], call, None, None)
Gio.bus_own_name(Gio.BusType.SESSION, "org.freedesktop.Notifications", 0, acquired, None, None)
GLib.MainLoop().run()
EOF
python3 -I $T/notifications.py >$T/notifications.log 2>&1 &
notifications=$!
sleep 1
ATL_BINARY=$T/atl "$src/builddir/atl-installer-service" >$T/installer.log 2>&1 &
installer=$!
trap 'pkill -f -- "--install-ask .*$T/cache" 2>/dev/null; kill $permission_store $notifications $installer 2>/dev/null; wait 2>/dev/null' EXIT
trap 'exit 1' INT TERM HUP
sleep 2
allow() { gdbus call --session --dest org.freedesktop.impl.portal.PermissionStore --object-path /org/freedesktop/impl/portal/PermissionStore \
	--method org.freedesktop.impl.portal.PermissionStore.SetPermission de.nexumia.atl true atl.update-without-asking de.nexumia.atl.$store "['$1']" >/dev/null; }

fail=0
check() { case $3 in *"$2"*) r=ok ;; *) r=FAIL; fail=1 ;; esac; echo "$r: $1: $3"; }
# an install that must show the page: wait for it to decide, then close it like Cancel would
asks() { # client args...
	lines=$(wc -l < $T/installer.log)
	"$@" > $T/reply 2>&1 &
	client=$!
	for i in $(seq 120); do
		tail -n +$((lines + 1)) $T/installer.log | grep -q -e 'asking, no update' -e 'updating without asking' -e 'install-flatpak: running' && break
		grep -q . $T/reply && break
		sleep 1
	done
	sleep 3 # a page shown without a decision line (no unattended option) needs the time to appear
	tail -n +$((lines + 1)) $T/installer.log | grep -e 'install: ' | grep -v 'running' | tail -n 1
	pkill -f -- "--install-ask .*$T/cache" 2>/dev/null
	wait $client 2>/dev/null
}
installed_version() { cmp -s $atl/apk0.apk "$new" && echo new || echo old; }

{
seed $store "$perms" 35
check "store not allowed: asks" "not allowed for this app store" "$(asks sandboxed python3 -I $T/client.py "$new" unattended)"
check "  ...not updated" old "$(installed_version)"

allow yes
check "allowed: updated without asking" "error=." "$(sandboxed timeout 300 python3 -I $T/client.py "$new" unattended)."
check "  ...no question asked" "updating without asking" "$(grep 'install: ' $T/installer.log | tail -n 3)"
check "  ...installed" new "$(installed_version)"
check "  ...installer kept" "-I $store" "$(tail -n 1 $T/make-app.log)"
check "  ...notification" "updated it to version" "$(cat $T/notifications.log)"
check "GetInstaller" "installer=$store" "$(sandboxed python3 -I $T/client.py get $pkg)"

seed org.example.other "$perms" 35
check "installed by another store: asks" "installed by someone else" "$(asks sandboxed python3 -I $T/client.py "$new" unattended)"
seed "" "$perms" 35
check "installed from a file: asks" "installed by someone else" "$(asks sandboxed python3 -I $T/client.py "$new" unattended)"
seed $store "org.scoutant.blokish.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" 35
check "a new permission: asks" "new permissions" "$(asks sandboxed python3 -I $T/client.py "$new" unattended)"
seed $store "$perms" 99
check "a lower target SDK: asks" "lower target SDK" "$(asks sandboxed python3 -I $T/client.py "$new" unattended)"
seed $store "$perms" 35
check "the same version: asks" "not an update" "$(asks sandboxed python3 -I $T/client.py "$old" unattended)"
check "no unattended option: asks" "absent" "$(asks sandboxed python3 -I $T/client.py "$new" | grep -q 'without asking' && echo present || echo absent)"
check "  ...not updated" old "$(installed_version)"
check "a caller outside a sandbox: asks" "absent" "$(asks python3 -I $T/client.py "$new" unattended | grep -q 'without asking' && echo present || echo absent)"
allow no
check "switched off again: asks" "not allowed for this app store" "$(asks sandboxed python3 -I $T/client.py "$new" unattended)"
check "  ...not updated" old "$(installed_version)"
} 2>&1 | tee $T/result.txt
echo "results in $T/result.txt, logs in $T"
# for headless-shot, which ends the display after its steps: -x 'until [ -e RESULT ]; do sleep 5; done'
[ -n "${AUTO_UPDATE_RESULT:-}" ] && cp $T/result.txt "$AUTO_UPDATE_RESULT"
! grep -q "^FAIL" $T/result.txt
