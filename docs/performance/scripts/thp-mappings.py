# Mappings that hold transparent huge pages (AnonHugePages) in raw smaps dumps.
import sys, gzip, re
for p in sys.argv[1:]:
    tot=0; rows=[]; cur=None
    for line in gzip.open(p,'rt',errors='replace'):
        m = re.match(r"^([0-9a-f]+)-([0-9a-f]+) (\S+) (\S+) (\S+) (\S+)\s*(.*)$", line)
        if m:
            cur={"name":m.group(7) or "(anon)","size":(int(m.group(2),16)-int(m.group(1),16))//1024,"start":m.group(1)}; continue
        k,_,v=line.partition(":"); v=v.strip()
        if cur is None or not v.endswith("kB"): continue
        cur[k]=int(v.split()[0])
        if k=="AnonHugePages" and cur[k]>0:
            tot+=cur[k]; rows.append(dict(cur))
    print("==",p,"AnonHugePages total %.1f MB in %d mappings" % (tot/1024,len(rows)))
    for r in sorted(rows,key=lambda r:-r["AnonHugePages"])[:14]:
        print("   huge %6d kB  rss %6d  size %7d  %s %s" % (r["AnonHugePages"],r.get("Rss",0),r["size"],r["start"],r["name"][:70]))
