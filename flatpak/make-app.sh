#!/bin/sh
# Turn one APK into its own Flatpak and install it into the user
# installation (docs/hardening-roadmap.md). Development tool for the
# prototype; the real installer is ATL's host side (phase 2).
#
# Usage: flatpak/make-app.sh [options] <apk> [split apks...]
#
#   -p PACKAGE  Android package name (required)
#   -n NAME     app name for the launcher (default: PACKAGE)
#   -i ICON     PNG or SVG icon
#   -b BASE     base app with the ATL engine (default: de.nexumia.atl.BaseApp)
#   -a ARGS     extra ATL arguments, e.g. "--sdk-int 26 -w 360 -h 720"
#   -f ARG      extra finish-arg, repeatable, e.g. -f --share=network
#   -r DIR      local repository (default: ~/.local/share/android_translation_layer/flatpak-repo)
#
# Every app gets only Wayland, the GPU and IPC, plus what -f adds. The
# app id is de.nexumia.atl.<PACKAGE>, so it cannot collide with a real
# Flatpak of the same name.
set -eu

base=de.nexumia.atl.BaseApp
repo=$HOME/.local/share/android_translation_layer/flatpak-repo
pkg= name= icon= atl_args= finish=

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit "${1:-0}"; }

while getopts "p:n:i:b:a:f:r:h" opt; do
	case $opt in
	p) pkg=$OPTARG ;;
	n) name=$OPTARG ;;
	i) icon=$OPTARG ;;
	b) base=$OPTARG ;;
	a) atl_args=$OPTARG ;;
	f) finish="$finish $OPTARG" ;;
	r) repo=$OPTARG ;;
	h) usage 0 ;;
	*) usage 1 ;;
	esac
done
shift $((OPTIND - 1))
[ $# -ge 1 ] && [ -n "$pkg" ] || usage 1
name=${name:-$pkg}
id=de.nexumia.atl.$pkg

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
mkdir -p "$build/files/share/applications"
cat > "$build/files/share/applications/$id.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=$name
Exec=atl-app
$icon_line
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
	--talk-name=org.sigxcpu.Feedback $finish >/dev/null
flatpak build-export "$repo" "$build" stable >/dev/null
flatpak remote-add --user --if-not-exists --no-gpg-verify atl-local "$repo"
flatpak remote-modify --user --url="file://$repo" atl-local
if flatpak info --user "$id" >/dev/null 2>&1; then
	flatpak update --user -y --noninteractive "$id"
else
	flatpak install --user -y --noninteractive atl-local "$id//stable"
fi
echo "installed $id"
