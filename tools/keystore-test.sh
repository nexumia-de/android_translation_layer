#!/bin/sh
# Runs tools/keystore-test/KeystoreTest.java against this checkout's keystore classes on a desktop
# JVM (JDK 17): the classes are copied, AndroidKeyStore's libsecret calls become a map and
# ATLAuth's D-Bus calls settable answers. No Android, no ATL build needed.
#   tools/keystore-test.sh
set -eu
ATL=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
src=$tmp/src
mkdir -p "$src/android/security/keystore" "$src/android/atl" "$src/android/util" "$src/android/os"
cp "$ATL"/src/api-impl/android/security/keystore/*.java "$src/android/security/keystore/"
cp "$ATL/tools/keystore-test/KeystoreTest.java" "$src/"
python3 - "$ATL" "$src" <<'PY'
import sys
atl, src = sys.argv[1], sys.argv[2]
ks = src + '/android/security/keystore/AndroidKeyStore.java'
s = open(ks).read()
natives = '''	private static native void nativeSetKey(String package_name, String alias, byte[] encoded);
	private static native byte[] nativeGetKey(String package_name, String alias);
	private static native void nativeDeleteKey(String package_name, String alias);
	private static native String[] nativeAliases(String package_name);'''
assert natives in s, 'AndroidKeyStore natives changed: update tools/keystore-test.sh'
s = s.replace(natives, '''	static final java.util.Map<String, byte[]> store = new java.util.TreeMap<>();
	private static void nativeSetKey(String p, String alias, byte[] encoded) { store.put(alias, encoded.clone()); }
	private static byte[] nativeGetKey(String p, String alias) { byte[] b = store.get(alias); return b == null ? null : b.clone(); }
	private static void nativeDeleteKey(String p, String alias) { store.remove(alias); }
	private static String[] nativeAliases(String p) { return store.keySet().toArray(new String[0]); }''')
s = s.replace('ATLLoadedApp.getPrimaryApplication().getApplication().getPackageName()', '"test"').replace('import android.atl.ATLLoadedApp;\n', '')
open(ks, 'w').write(s)
a = open(atl + '/src/api-impl/android/atl/ATLAuth.java').read()
native = 'private static native String nativeCall(String method);'
assert native in a, 'ATLAuth native changed: update tools/keystore-test.sh'
a = a.replace(native, '''public static String fakeAuthenticators = "15 abc", fakeLocked = "false";
	private static String nativeCall(String method) { return method.equals("Authenticators") ? fakeAuthenticators : method.equals("DeviceLocked") ? fakeLocked : "unavailable"; }
	public static void resetCache() { enrollment = null; locked_asked_at = 0; }''')
open(src + '/android/atl/ATLAuth.java', 'w').write(a)
PY
cat > "$src/android/atl/ATLPaths.java" <<'J'
package android.atl;
public class ATLPaths { public static final boolean in_flatpak = true; }
J
cat > "$src/android/util/Slog.java" <<'J'
package android.util;
public class Slog { public static void w(String t, String m) { System.err.println("W/" + t + ": " + m); } public static void i(String t, String m) {} }
J
cat > "$src/android/os/SystemClock.java" <<'J'
package android.os;
public class SystemClock { public static long offset; public static long elapsedRealtime() { return System.nanoTime() / 1000000 + offset; } }
J
"$JAVA_HOME/bin/javac" -nowarn -d "$tmp/out" $(find "$src" -name '*.java') 2>&1 | grep -v '^Note:' || true
"$JAVA_HOME/bin/java" -cp "$tmp/out" KeystoreTest
