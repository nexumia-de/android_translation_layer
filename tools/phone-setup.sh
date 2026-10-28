#!/bin/sh
# Build Android Translation Layer and its dependencies into ~/.local on the
# phone (Nura/postmarketOS edge, aarch64), as the unprivileged user.
#
# Usage:
#   tools/phone-setup.sh [step...]
#
# Steps (default: all build steps in this order):
#   check      check the system packages (see docs/dev-environment.md)
#   libunwind  unpack libunwind-dev into $PREFIX/opt (see below)
#   bionic     build bionic_translation
#   art        build art_standalone (the long one: about an hour)
#   opensles   build libopensles-standalone
#   atl        build ATL from this checkout
#   env        print the environment for running ATL from $PREFIX,
#              use as: eval "$(tools/phone-setup.sh env)"
#
# Variables: PREFIX (default ~/.local), SRC (dependency checkouts, default
# ~/work/deps), JOBS (parallel jobs, default 4: 8 jobs run out of memory
# next to the owner's session).
#
# libunwind-dev conflicts with llvm-libunwind, which libc++ needs (imsd,
# fingerprintd), so it cannot be installed system-wide. Its runtime package
# libunwind is installed; the libunwind step downloads the -dev package as
# this user and points its .pc files and .so links at the system library.
set -eu

PREFIX=${PREFIX:-$HOME/.local}
SRC=${SRC:-$HOME/work/deps}
JOBS=${JOBS:-4}
ATL=$(cd "$(dirname "$0")/.." && pwd)

GITLAB=https://gitlab.com/android_translation_layer
BIONIC_COMMIT=6253dec7f24a57a94039a088cb436d880e92e3be
ART_COMMIT=a1edfc40e8cea3644d80e4e776d3fa7fc397a1c2
OPENSLES_COMMIT=c6982620c7dadeb90220b23c575b25eeffda765f

UNWIND=$PREFIX/opt/libunwind-dev

packages="build-base meson samurai bash zip python3 java-common java-cacerts openjdk8-jdk openjdk17-jdk maven
  elfutils-dev libbsd-dev libucontext-dev mesa-dev
  bsd-compat-headers expat-dev icu-dev libcap-dev libpng-dev libselinux-dev lz4-dev openssl-dev
  valgrind-dev wolfssl-jni-dev xz-dev zlib-dev vixl-dev
  libsndfile-dev sdl2-compat-dev
  alsa-lib-dev ffmpeg-dev fontconfig-dev glib-dev gtk4.0-dev gtk4-layer-shell-dev libdrm-dev
  libgudev-dev libportal-dev libsecret-dev openxr-dev vulkan-loader-dev wayland-protocols webkit2gtk-6.0-dev
  cage grim wlr-randr wlrctl libunwind"

log() { printf '\n==> %s\n' "$*"; }

# Use JDK $1 (/usr/lib/jvm/java-$1-openjdk) for the rest of the step,
# independent of the system's default JVM: art_standalone is built with
# JDK 8 like Alpine's package, ATL with JDK 17 like upstream's nightlies
# (its tools/jni_upcall_gen needs Java 12 or newer).
use_jdk() {
	JAVA_HOME=/usr/lib/jvm/java-$1-openjdk
	[ -x "$JAVA_HOME/bin/javac" ] || { echo "JDK $1 not found in $JAVA_HOME" >&2; exit 1; }
	PATH=$JAVA_HOME/bin:$PATH
	export JAVA_HOME PATH
}

# Environment for building against and running from $PREFIX.
print_env() {
	cat <<EOF
export PATH="$PREFIX/bin:\$PATH"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig:$UNWIND/lib/pkgconfig\${PKG_CONFIG_PATH:+:\$PKG_CONFIG_PATH}"
export LIBRARY_PATH="$PREFIX/lib\${LIBRARY_PATH:+:\$LIBRARY_PATH}"
export CPATH="$PREFIX/include\${CPATH:+:\$CPATH}"
export LD_LIBRARY_PATH="$PREFIX/lib\${LD_LIBRARY_PATH:+:\$LD_LIBRARY_PATH}"
export XDG_DATA_DIRS="$PREFIX/share:\${XDG_DATA_DIRS:-/usr/local/share:/usr/share}"
EOF
}

# Clone $1 into $SRC and check out commit $2.
checkout() {
	mkdir -p "$SRC"
	[ -d "$SRC/$1/.git" ] || git clone "$GITLAB/$1.git" "$SRC/$1"
	git -C "$SRC/$1" cat-file -e "$2^{commit}" 2>/dev/null || git -C "$SRC/$1" fetch origin
	git -C "$SRC/$1" -c advice.detachedHead=false checkout "$2"
}

# meson setup/compile/install of $1 with the remaining arguments.
meson_build() {
	dir=$1
	shift
	meson setup --reconfigure "$dir/builddir" "$dir" --prefix "$PREFIX" --libdir lib \
		--buildtype debugoptimized "$@"
	meson compile -C "$dir/builddir" -j "$JOBS"
	meson install -C "$dir/builddir"
}

step_check() {
	log "Checking system packages"
	# shellcheck disable=SC2086
	missing=$(for p in $packages; do apk info -e "$p" >/dev/null || echo "$p"; done)
	if [ -n "$missing" ]; then
		echo "Missing packages, install as root:"
		echo "  apk add" $missing
		exit 1
	fi
	echo "All packages installed."
}

step_libunwind() {
	log "Unpacking libunwind-dev into $UNWIND"
	version=$(apk info -e libunwind >/dev/null && apk list -I libunwind | sed -E 's/^libunwind-([^ ]+) .*/\1/')
	tmp=$(mktemp -d)
	# "apk fetch libunwind-dev=<version>" fails on the conflict, so fetch by
	# name and compare the version.
	(cd "$tmp" && apk fetch libunwind-dev >/dev/null)
	[ -f "$tmp/libunwind-dev-$version.apk" ] || {
		echo "libunwind-dev does not match the installed libunwind $version:" "$tmp"/*.apk >&2
		exit 1
	}
	rm -rf "$UNWIND"
	mkdir -p "$UNWIND"
	tar -xzf "$tmp/libunwind-dev-$version.apk" -C "$tmp" usr
	cp -a "$tmp/usr/." "$UNWIND/"
	rm -rf "$tmp"
	# A system-wide install puts unwind.h in /usr/include, where GCC's own
	# unwind.h wins. With -I it would shadow GCC's, so leave it out.
	rm "$UNWIND/include/unwind.h"
	# Links to versioned files go to the runtime package in /usr/lib; links
	# between the -dev links (libunwind-generic.so) stay as they are.
	for link in "$UNWIND"/lib/*.so; do
		target=$(readlink "$link")
		case $target in
		*.so) ;;
		*) [ -e "/usr/lib/$target" ] && ln -sfn "/usr/lib/$target" "$link" ;;
		esac
	done
	sed -i "s|^prefix=.*|prefix=$UNWIND|" "$UNWIND"/lib/pkgconfig/*.pc
	PKG_CONFIG_PATH=$UNWIND/lib/pkgconfig pkg-config --modversion libunwind
}

step_bionic() {
	log "Building bionic_translation"
	checkout bionic_translation "$BIONIC_COMMIT"
	meson_build "$SRC/bionic_translation"
}

step_art() {
	log "Building art_standalone (long build)"
	checkout art_standalone "$ART_COMMIT"
	use_jdk 1.8
	make -C "$SRC/art_standalone" -j "$JOBS" ____PREFIX="$PREFIX" ____LIBDIR=lib
	make -C "$SRC/art_standalone" ____PREFIX="$PREFIX" ____LIBDIR=lib ____INSTALL_ETC="$PREFIX/etc" install
}

step_opensles() {
	log "Building libopensles-standalone"
	checkout libopensles-standalone "$OPENSLES_COMMIT"
	meson_build "$SRC/libopensles-standalone"
}

step_atl() {
	log "Building ATL from $ATL"
	use_jdk 17
	meson_build "$ATL" -Ddownload_tool_deps=true
}

[ $# -gt 0 ] || set -- check libunwind bionic art opensles atl

if [ "$1" = env ]; then
	print_env
	exit 0
fi

eval "$(print_env)"
for step in "$@"; do
	case $step in
	# A subshell per step, so use_jdk applies to that step only.
	check | libunwind | bionic | art | opensles | atl) ("step_$step") ;;
	*) echo "unknown step: $step" >&2; exit 1 ;;
	esac
done
