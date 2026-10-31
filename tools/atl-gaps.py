#!/usr/bin/env python3
"""List what an Android app uses that ATL lacks or only stubs.

Usage: python3 -I tools/atl-gaps.py APK_DIR [--out DIR] [--app-prefix com.example[,org.other]]
                                [--dexdump PATH] [--framework JAR ...]

APK_DIR holds the app's APKs (base.apk and splits, or a single .apk). The app's
dex files and ATL's framework (api-impl.jar and ART's boot jars from ~/.local)
are disassembled with art_standalone's dexdump2; the dumps are cached in
OUT/dump. Every reference from app code into a framework package is resolved
through the class hierarchy (app classes extending framework classes too).
Unresolved ones are written to OUT/gaps.md and OUT/gaps.tsv, with callers split
into the app's own code (--app-prefix, default from the APK file or directory
name), obfuscated code (R8 moves app and library code there) and named
libraries. References behind SDK_INT checks are listed too; they may never run.

References that resolve to a method of api-impl with an empty body are listed
as kind "stub": it only returns null, 0, false or -1, or does nothing but log
"not implemented" or "not handled". Hooks an app overrides (on*) and constructors are left out.
This finds empty stubs on screens no test reached. It cannot see a method that
does something but not enough; the run-time stub log and a comparison with real
Android are needed for that (docs/app-bring-up.md).
"""
import argparse
import collections
import os
import re
import subprocess
import sys
import zipfile

HOME = os.path.expanduser("~")
DEFAULT_DEXDUMP = HOME + "/work/deps/art_standalone/out/host/linux-x86/bin/dexdump2"
DEFAULT_FRAMEWORK = [HOME + "/.local/lib/java/dex/android_translation_layer/api-impl.jar"] + [
    HOME + "/.local/lib/java/dex/art/" + j for j in (
        "core-oj-hostdex.jar", "core-libart-hostdex.jar", "apache-xml-hostdex.jar",
        "apachehttp-hostdex.jar", "bouncycastle-hostdex.jar", "okhttp-hostdex.jar",
        "wolfssljni-hostdex.jar")]
# packages the platform provides; an app's own copy of a class there is ignored
FRAMEWORK_PREFIXES = ("android/", "java/", "javax/", "dalvik/", "libcore/", "sun/",
                      "org/json/", "org/xml/", "org/xmlpull/", "org/w3c/", "org/apache/http/")

REF_RE = re.compile(r"(L[^;\s]+;)\.([^:\s]+):(\S+) // (method|field)@")
TYPE_RE = re.compile(r"(?:new-instance|const-class|check-cast|instance-of|new-array)[^,]*, \[*(L[^;\s]+;) // type@")
HEADER_RE = re.compile(r"\|\[[0-9a-f]+\] (\S+)\.([^.:]+):(\S+)")
INSN_RE = re.compile(r"\|[0-9a-f]{4}: (.*)")
DEFAULT_CONST_RE = re.compile(r"const[\w/-]* v\d+, #\w+ (0|-1|0\.000000)\b")
HOOK_RE = re.compile(r"<|on[A-Z]")
STUB_TEXT_RE = re.compile(r'const-string[\w/]* v\d+, ".*(not implemented|not handled|stub).*"', re.I)
# first match wins; class names with dots, longest prefixes first
FEATURES = (
    ("Camera", ("android.hardware.camera", "android.hardware.Camera", "android.media.Image")),
    ("Location", ("android.location.",)),
    ("Notifications", ("android.app.Notification", "android.service.notification.")),
    ("NFC", ("android.nfc.",)),
    ("Biometrics", ("android.hardware.biometrics.", "android.hardware.fingerprint.")),
    ("Bluetooth", ("android.bluetooth.",)),
    ("Sensors", ("android.hardware.Sensor", "android.hardware.display.", "android.hardware.input.")),
    ("Speech", ("android.speech.",)),
    ("Picture in picture", ("android.app.PictureInPicture",)),
    ("Audio and video", ("android.media.", "android.view.Surface", "android.widget.VideoView")),
    ("WebView", ("android.webkit.",)),
    ("Printing", ("android.print.", "android.graphics.pdf.")),
    ("Accessibility", ("android.view.accessibility.", "android.accessibilityservice.")),
    ("Autofill", ("android.view.autofill.", "android.service.autofill.", "android.view.ViewStructure")),
    ("Text input", ("android.view.inputmethod.", "android.view.textclassifier.", "android.view.textservice.")),
    ("Clipboard", ("android.content.ClipboardManager", "android.content.ClipData", "android.content.ClipDescription")),
    ("Contacts, calendar, media store", ("android.provider.",)),
    ("Telephony", ("android.telephony.",)),
    ("Background work", ("android.app.job.", "android.app.AlarmManager", "android.app.Service", "android.app.ForegroundService")),
    ("Accounts", ("android.accounts.",)),
    ("Crypto and keystore", ("android.security.", "java.security.", "javax.crypto.", "javax.net.ssl.")),
    ("Network", ("android.net.", "java.net.")),
    ("Files and storage", ("android.os.storage.", "android.os.ParcelFile", "android.app.usage.", "java.io.", "java.nio.")),
    ("Graphics", ("android.graphics.", "android.opengl.", "android.renderscript.")),
    ("Animation", ("android.animation.", "android.view.animation.", "android.transition.")),
    ("Views and widgets", ("android.view.", "android.widget.", "android.text.", "android.app.")),
    ("App framework", ("android.content.", "android.os.", "android.util.", "android.database.")),
    ("Java library", ("java.", "javax.", "dalvik.", "libcore.", "sun.", "org.")),
)


class Hierarchy:
    def __init__(self):
        self.supers = {}      # class -> [superclass, interfaces...]
        self.methods = {}     # class -> set of "name:sig"
        self.fields = {}      # class -> set of names


def dexdump(dexdump_bin, dex, out_txt, code):
    if os.path.exists(out_txt) and os.path.getsize(out_txt) > 0:
        return
    env = dict(os.environ)
    env["LD_LIBRARY_PATH"] = os.path.join(os.path.dirname(os.path.dirname(dexdump_bin)), "lib64")
    args = [dexdump_bin] + (["-d"] if code else []) + [dex]
    with open(out_txt + ".part", "w") as f:
        subprocess.run(args, stdout=f, stderr=subprocess.DEVNULL, env=env, check=True)
    os.rename(out_txt + ".part", out_txt)


def extract_dex(archive, dest):
    os.makedirs(dest, exist_ok=True)
    found = []
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                path = os.path.join(dest, name)
                if not os.path.exists(path):
                    with open(path, "wb") as f:
                        f.write(z.read(name))
                found.append(path)
    return found


def feature(key):
    for name, prefixes in FEATURES:
        if key.startswith(prefixes):
            return name
    return "Other"


def is_stub_body(insns):
    """Does a method with these instructions do nothing but return a default value?"""
    ops = [i.split()[0] for i in insns]
    if any(o == "throw" for o in ops):
        return False   # a deliberate failure shows up by itself
    if len(insns) <= 8 and any(STUB_TEXT_RE.search(i) for i in insns):
        return True
    if not ops or not ops[-1].startswith("return"):
        return False
    if len(ops) == 1:
        return ops[0] == "return-void"
    return len(ops) == 2 and bool(DEFAULT_CONST_RE.match(insns[0]))


def find_stubs(txt, stubs):
    """Add class -> "name:sig" of the empty methods in a dump with code to stubs."""
    current = None
    insns = []

    def flush():
        if current and not HOOK_RE.match(current[1]) and is_stub_body(insns):
            stubs[current[0]].add(current[1] + ":" + current[2])

    with open(txt, errors="replace") as f:
        for line in f:
            if "|" not in line:
                continue
            m = HEADER_RE.search(line)
            if m:
                flush()
                current = m.group(1).replace(".", "/"), m.group(2), m.group(3)
                insns = []
                continue
            m = INSN_RE.search(line)
            if m and current:
                insns.append(m.group(1))
    flush()


def drop_overridden(stubs, fw):
    """A base method that a framework subclass implements is not a stub of that subclass's users."""
    children = collections.defaultdict(list)
    for c, supers in fw.supers.items():
        for s in supers:
            children[s].append(c)
    for c in list(stubs):
        stack = list(children[c])
        while stack and stubs[c]:
            sub = stack.pop()
            stack.extend(children[sub])
            stubs[c] -= (stubs[c] & fw.methods.get(sub, set())) - stubs.get(sub, set())


def parse(txt, hier, refs=None):
    """Read declarations (and with refs, a Counter of each method's references) from a dump."""
    cls = section = None
    pending_name = None
    caller = None
    with open(txt, errors="replace") as f:
        for line in f:
            if "|" in line:
                if refs is None:
                    continue
                m = HEADER_RE.search(line)
                if m:
                    caller = m.group(1)
                    continue
                if "//" not in line or caller is None:
                    continue
                m = REF_RE.search(line)
                if m:
                    refs[(m.group(1)[1:-1], m.group(2), m.group(3), m.group(4), caller)] += 1
                    continue
                m = TYPE_RE.search(line)
                if m:
                    refs[(m.group(1)[1:-1], None, None, "type", caller)] += 1
                continue
            s = line.strip()
            if s.startswith("Class descriptor"):
                cls = s.split("'")[1][1:-1]
                hier.supers.setdefault(cls, [])
                hier.methods.setdefault(cls, set())
                hier.fields.setdefault(cls, set())
                section = None
            elif s.startswith("Superclass"):
                if "'" in s:
                    hier.supers[cls].append(s.split("'")[1][1:-1])
            elif s.startswith("Interfaces"):
                section = "interfaces"
            elif s.startswith(("Static fields", "Instance fields")):
                section = "fields"
            elif s.startswith(("Direct methods", "Virtual methods")):
                section = "methods"
            elif s.startswith("#") and section == "interfaces" and "'" in s:
                hier.supers[cls].append(s.split("'")[1][1:-1])
            elif s.startswith("name") and "'" in s:
                pending_name = s.split("'")[1]
                if section == "fields":
                    hier.fields[cls].add(pending_name)
            elif s.startswith("type") and "'" in s and section == "methods" and pending_name:
                hier.methods[cls].add(pending_name + ":" + s.split("'")[1])
                pending_name = None


def is_framework(cls):
    return cls.startswith(FRAMEWORK_PREFIXES)


def resolve(cls, member, kind, fw, app, cache):
    """Return (declaring class, None) if found, else (None, (lowest framework class on the way, missing class or not)).

    Like the boot class loader, the framework wins over the app; a class in a
    framework package that only the app ships (android.support) is the app's.
    """
    key = (cls, member, kind)
    if key in cache:
        return cache[key]
    seen = set()
    stack = [cls]
    first_fw = None
    missing_class = None
    while stack:
        c = stack.pop(0)
        if c in seen:
            continue
        seen.add(c)
        h = fw if c in fw.supers else app
        if c not in h.supers:
            if is_framework(c):
                missing_class = missing_class or c
                first_fw = first_fw or c
            continue
        if h is fw:
            first_fw = first_fw or c
        decl = h.methods[c] if kind == "method" else h.fields[c]
        if member in decl:
            cache[key] = (c, None)
            return cache[key]
        stack.extend(h.supers[c])
    result = (None, (first_fw, missing_class) if first_fw else None)
    cache[key] = result
    return result


def caller_group(caller, app_prefixes):
    if caller.startswith(app_prefixes):
        return "app"
    if all(len(p) <= 2 for p in caller.split(".")[:-1]):
        return "obfuscated"
    return "lib " + ".".join(caller.split(".")[:2])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("apk_dir")
    ap.add_argument("--out")
    ap.add_argument("--app-prefix")
    ap.add_argument("--dexdump", default=DEFAULT_DEXDUMP)
    ap.add_argument("--framework", nargs="*", default=DEFAULT_FRAMEWORK)
    a = ap.parse_args()

    apk_dir = os.path.abspath(a.apk_dir)
    name = os.path.basename(apk_dir.rstrip("/"))
    out = os.path.abspath(a.out or os.path.join(HOME, "scratch-gaps", name))
    app_prefix = a.app_prefix or re.sub(r"_[^_]*$", "", name.removesuffix(".apk"))
    app_prefixes = tuple(p + "." for p in app_prefix.split(","))
    dump = os.path.join(out, "dump")

    fw = Hierarchy()
    stubs = collections.defaultdict(set)   # class -> "name:sig" of empty methods
    for jar in a.framework:
        jname = os.path.basename(jar)
        for dex in extract_dex(jar, os.path.join(dump, "framework", jname)):
            txt = dex[:-4] + ".txt"
            code = jname == "api-impl.jar"
            dexdump(a.dexdump, dex, txt, code)
            parse(txt, fw)
            if code:
                find_stubs(txt, stubs)

    drop_overridden(stubs, fw)

    apks = [os.path.join(apk_dir, f) for f in sorted(os.listdir(apk_dir)) if f.endswith(".apk")] \
        if os.path.isdir(apk_dir) else [apk_dir]
    app = Hierarchy()
    refs = collections.Counter()
    for apk in apks:
        for dex in extract_dex(apk, os.path.join(dump, "app", os.path.basename(apk))):
            txt = dex[:-4] + ".txt"
            dexdump(a.dexdump, dex, txt, code=True)
            print("reading", txt, file=sys.stderr)
            parse(txt, app, refs)

    gaps = collections.defaultdict(collections.Counter)   # "Class.member:sig" -> group -> count
    kinds = {}
    cache = {}
    for (cls, member, sig, kind, caller), count in refs.items():
        if kind == "type":
            if not is_framework(cls) or cls in fw.supers or cls in app.supers:
                continue
            key, k = cls.replace("/", "."), "class"
        else:
            if not is_framework(cls) and cls not in app.supers:
                continue   # a library class the app does not ship, not ATL's business
            m = member + ":" + sig if kind == "method" else member
            declaring, r = resolve(cls, m, kind, fw, app, cache)
            if declaring:
                if kind != "method" or m not in stubs.get(declaring, ()):
                    continue
                key, k = declaring.replace("/", ".") + "." + m, "stub"
            elif r is None:
                continue
            elif r[1]:
                missing_class = r[1]
                key, k = missing_class.replace("/", "."), "class"
            else:
                key, k = r[0].replace("/", ".") + "." + m, kind
        gaps[key][caller_group(caller, app_prefixes)] += count
        kinds[key] = k

    def order(item):
        key, groups = item
        own = groups.get("app", 0) + groups.get("obfuscated", 0)
        return (-own, -sum(groups.values()), key)

    rows = sorted(gaps.items(), key=order)
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "gaps.tsv"), "w") as f:
        f.write("kind\tfeature\tmissing\tapp\tobfuscated\tlibraries\n")
        for key, groups in rows:
            libs = ", ".join("%s %d" % (g[4:], n) for g, n in groups.most_common() if g.startswith("lib "))
            f.write("%s\t%s\t%s\t%d\t%d\t%s\n" % (kinds[key], feature(key), key, groups.get("app", 0), groups.get("obfuscated", 0), libs))
    with open(os.path.join(out, "gaps.md"), "w") as f:
        f.write("# ATL gaps for %s\n\n" % name)
        f.write("References from app code (prefix `%s`), obfuscated code and libraries into the framework that ATL does not resolve (kind class, method, field) or only stubs (kind stub). Counts are call sites.\n\n" % app_prefix)
        f.write("## By feature\n\nEntries, then those with a caller in the app's own code in brackets.\n\n| feature | missing | stubs |\n|---|---|---|\n")
        table = collections.defaultdict(lambda: [0, 0, 0, 0])
        for key, groups in rows:
            t = table[feature(key)]
            i = 2 if kinds[key] == "stub" else 0
            t[i] += 1
            t[i + 1] += groups.get("app", 0) > 0
        for name, t in sorted(table.items(), key=lambda kv: -kv[1][1] - kv[1][3]):
            f.write("| %s | %d (%d) | %d (%d) |\n" % (name, t[0], t[1], t[2], t[3]))
        f.write("\n")
        for title, want in (("Called from the app or obfuscated code", True), ("Called only from named libraries", False)):
            f.write("## %s\n\n| kind | missing | app | obfuscated | libraries |\n|---|---|---|---|---|\n" % title)
            for key, groups in rows:
                own = groups.get("app", 0) + groups.get("obfuscated", 0)
                if (own > 0) != want:
                    continue
                libs = ", ".join("%s %d" % (g[4:], n) for g, n in groups.most_common() if g.startswith("lib "))
                f.write("| %s | `%s` | %d | %d | %s |\n" % (kinds[key], key, groups.get("app", 0), groups.get("obfuscated", 0), libs))
            f.write("\n")
    print("%d gaps and stubs, written to %s/gaps.md and gaps.tsv" % (len(rows), out), file=sys.stderr)


if __name__ == "__main__":
    main()
