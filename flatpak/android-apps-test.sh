#!/bin/sh
# Headless check of --android-apps (src/main-executable/android_apps.c) with fake installed apps in a
# temporary Flatpak user directory and configuration, and a stand-in atl-push-server that records
# its calls. Run inside a display with its own session bus, from the build directory, e.g.
#   ANDROID_APPS_TEST_WAIT=40 tools/headless-shot.sh -d 10 -w 4 -x "tap ..." -- ../flatpak/android-apps-test.sh
# then read the checks it prints. Use flock -o with the shared lock.
T=$(mktemp -d)
export FLATPAK_USER_DIR=$T/flatpak XDG_CONFIG_HOME=$T/config XDG_DATA_HOME=$T/data PATH=$T/bin:$PATH
mkdir -p "$T/bin" "$FLATPAK_USER_DIR/exports/share/applications" "$XDG_CONFIG_HOME"
for app in org.example.store org.example.maps; do
	id=de.nexumia.atl.$app
	mkdir -p "$FLATPAK_USER_DIR/app/$id/current/active"
	printf '[Desktop Entry]\nType=Application\nName=%s\nExec=true\n' "$app" > "$FLATPAK_USER_DIR/exports/share/applications/$id.desktop"
	printf '[Application]\nname=%s\n' "$id" > "$FLATPAK_USER_DIR/app/$id/current/active/metadata"
done
printf '[Session Bus Policy]\nde.nexumia.atl.Installer=talk\n' >> "$FLATPAK_USER_DIR/app/de.nexumia.atl.org.example.store/current/active/metadata"
cat > "$T/bin/atl-push-server" <<X
#!/bin/sh
echo "\$@" >> $T/push-calls
case \$1 in get) cat $T/push-url 2>/dev/null || echo https://unifiedpush.kde.org/ ;; set) echo "\$2" > $T/push-url ;; reset) rm -f $T/push-url ;; esac
X
chmod +x "$T/bin/atl-push-server"
${ATL:-./android-translation-layer} --android-apps >$T/app.log 2>&1 &
app=$!
trap 'kill $app 2>/dev/null; wait 2>/dev/null' EXIT
trap 'exit 1' INT TERM HUP
sleep ${ANDROID_APPS_TEST_WAIT:-40}
fail=0
check() { case $3 in *"$2"*) r=ok ;; *) r=FAIL; fail=1 ;; esac; echo "$r: $1: $3"; }
check "push server read" "get" "$(head -n 1 $T/push-calls 2>&1)"
check "push server set" "set https://push.example.org" "$(cat $T/push-calls 2>&1 | tr '\n' ' ')"
check "store may no longer install" "!talk" "$(cat $FLATPAK_USER_DIR/overrides/de.nexumia.atl.org.example.store 2>&1)"
check "navigation saved" "mode=gesture" "$(cat $XDG_CONFIG_HOME/android_translation_layer/settings.ini 2>&1)"
check "  ...and given to every app" "ATL_NAVIGATION=gesture" "$(cat $FLATPAK_USER_DIR/overrides/de.nexumia.atl.org.example.maps 2>&1)"
echo "files in $T"
sleep 3
exit $fail
