#!/bin/sh
# Turn one APK into its own Flatpak and install it into the user
# installation (docs/hardening-roadmap.md). ATL's --install-flatpak runs
# it (installed as libexec/android-translation-layer/make-app-flatpak)
# with the name, icon, URL schemes and permissions read from the APK;
# by hand it is a development tool.
#
# Usage: flatpak/make-app.sh [options] <apk> [split apks...]
#
#   -p PACKAGE  Android package name (required)
#   -n NAME     app name for the launcher (default: PACKAGE)
#   -i ICON     PNG or SVG icon
#   -b BASE     base app with the ATL engine (default: de.nexumia.atl.BaseApp)
#   -a ARGS     extra ATL arguments, e.g. "--sdk-int 26 -w 360 -h 720"
#   -m TYPES    MIME types the launcher handles, ';'-separated
#               (x-scheme-handler/<scheme> from the APK's intent filters)
#   -P PERMS    Android permissions the APK requests, ';'-separated;
#               mapped to Flatpak permissions (phase 4 table)
#   -f ARG      extra finish-arg, repeatable, e.g. -f --share=network
#   -r DIR      local repository (default: ~/.local/share/android_translation_layer/flatpak-repo)
#
# Every app gets Wayland, the GPU, IPC, feedbackd (vibration) and audio
# playback, plus what -P maps and -f adds. The app id is
# de.nexumia.atl.<PACKAGE>, so it cannot collide with a real Flatpak of
# the same name.
set -eu

base=de.nexumia.atl.BaseApp
repo=$HOME/.local/share/android_translation_layer/flatpak-repo
pkg= name= icon= atl_args= finish= mime_types= permissions=

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit "${1:-0}"; }

while getopts "p:n:i:b:a:m:P:f:r:h" opt; do
	case $opt in
	p) pkg=$OPTARG ;;
	n) name=$OPTARG ;;
	i) icon=$OPTARG ;;
	b) base=$OPTARG ;;
	a) atl_args=$OPTARG ;;
	m) mime_types=$OPTARG ;;
	P) permissions=$OPTARG ;;
	f) finish="$finish $OPTARG" ;;
	r) repo=$OPTARG ;;
	h) usage 0 ;;
	*) usage 1 ;;
	esac
done
shift $((OPTIND - 1))
[ $# -ge 1 ] && [ -n "$pkg" ] || usage 1
# The package name, label and schemes come from the APK: check the name
# like Android does, and keep the label to one line of a desktop file.
if ! printf '%s\n' "$pkg" | grep -Eqx '[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+'; then
	echo "invalid package name: $pkg" >&2
	exit 1
fi
name=$(printf '%s' "${name:-$pkg}" | tr '\000-\037\177' ' ')
id=de.nexumia.atl.$pkg

# Android permission -> Flatpak permission (docs/hardening-roadmap.md,
# phase 4); the rest needs portals or the broker and is not granted.
for perm in $(printf '%s' "$permissions" | tr ';' ' '); do
	case $perm in
	android.permission.INTERNET) finish="$finish --share=network" ;;
	# app stores: the host's installer service, which asks the user for every install
	android.permission.REQUEST_INSTALL_PACKAGES) finish="$finish --talk-name=de.nexumia.atl.Installer" ;;
	esac
done

mime_line=
exec_args=
for type in $(printf '%s' "$mime_types" | tr ';' ' '); do
	if printf '%s\n' "$type" | grep -Eqx 'x-scheme-handler/[A-Za-z][A-Za-z0-9+.-]*'; then
		mime_line="$mime_line$type;"
	fi
done
if [ -n "$mime_line" ]; then
	mime_line="MimeType=$mime_line"
	exec_args=" --uri %u"
fi

base_dir=$(flatpak info --user --show-location "$base" 2>/dev/null || flatpak info --show-location "$base")
runtime=$(flatpak info --show-runtime "$base")

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
build=$work/build
mkdir -p "$build/files"

cp -a "$base_dir/files/." "$build/files/"
rm -f "$build/files/manifest.json"

# the APK first, split APKs after it, as ATL takes them on the command line
apks=
i=0
for apk in "$@"; do
	install -D -m 644 "$apk" "$build/files/share/atl/apk$i.apk"
	apks="$apks /app/share/atl/apk$i.apk"
	i=$((i + 1))
done

cat > "$build/files/bin/atl-app" <<EOF
#!/bin/sh
exec android-translation-layer --gapplication-app-id=$id $atl_args$apks "\$@"
EOF
chmod 755 "$build/files/bin/atl-app"

icon_line=
if [ -n "$icon" ]; then
	case $icon in
	*.svg) install -D -m 644 "$icon" "$build/files/share/icons/hicolor/scalable/apps/$id.svg" ;;
	*) install -D -m 644 "$icon" "$build/files/share/icons/hicolor/512x512/apps/$id.png" ;;
	esac
	icon_line="Icon=$id"
fi
# D-Bus activation, as ATL's launchers outside a sandbox: a link or Open reaches the running app
# instead of starting a second one; Flatpak exports the service file and runs it in the sandbox
mkdir -p "$build/files/share/dbus-1/services"
cat > "$build/files/share/dbus-1/services/$id.service" <<EOF
[D-BUS Service]
Name=$id
Exec=/app/bin/atl-app --dbus-activated
EOF

mkdir -p "$build/files/share/applications"
cat > "$build/files/share/applications/$id.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=$name
DBusActivatable=true
Exec=atl-app$exec_args
$icon_line
$mime_line
X-Purism-FormFactor=Workstation;Mobile;
StartupNotify=true
EOF

cat > "$build/metadata" <<EOF
[Application]
name=$id
runtime=$runtime
command=atl-app
EOF

# shellcheck disable=SC2086 # $finish is a list of arguments
flatpak build-finish "$build" --socket=wayland --device=dri --share=ipc \
	--socket=pulseaudio --talk-name=org.sigxcpu.Feedback $finish >/dev/null
flatpak build-export "$repo" "$build" stable >/dev/null
flatpak remote-add --user --if-not-exists --no-gpg-verify atl-local "$repo"
flatpak remote-modify --user --url="file://$repo" atl-local
# not "flatpak info": Flatpak 1.18.4 aborts in it for apps from atl-local (exit status 134), which
# looked like "not installed", so updates were skipped
if flatpak list --user --app --columns=application | grep -qxF "$id"; then
	flatpak update --user -y --noninteractive "$id"
else
	flatpak install --user -y --noninteractive atl-local "$id//stable"
fi
echo "installed $id"
