# Private dirty (with swap) and private clean memory per perf-smaps.py category of raw smaps dumps; run from the repo root.
import sys, gzip, re, importlib.util, collections
spec = importlib.util.spec_from_file_location("ps", "tools/perf-smaps.py"); ps = importlib.util.module_from_spec(spec); spec.loader.exec_module(ps)
def load(p):
    maps=[]; cur=None
    for line in gzip.open(p,'rt',errors='replace'):
        m = re.match(r"^([0-9a-f]+)-([0-9a-f]+) (\S+) (\S+) (\S+) (\S+)\s*(.*)$", line)
        if m:
            cur={"name":m.group(7),"start":int(m.group(1),16)}; maps.append(cur); continue
        k,_,v=line.partition(":"); v=v.strip()
        if cur is not None and v.endswith("kB"): cur[k]=int(v.split()[0])
    return maps
cols=[]
for p in sys.argv[1:]:
    d=collections.Counter(); c=collections.Counter()
    for m in load(p):
        cat=ps.classify(m["name"], m["start"])
        d[cat]+=m.get("Private_Dirty",0)+m.get("Swap",0); c[cat]+=m.get("Private_Clean",0)
    cols.append((p,d,c))
cats=sorted(set().union(*[set(d)|set(c) for _,d,c in cols]), key=lambda k:-cols[-1][1][k])
print("category".ljust(14)+"".join(("%s" % p.split('/')[-2][-22:]).rjust(26) for p,_,_ in cols)); print(" "*14+"   dirty / clean (MB)".rjust(26)*len(cols))
for k in cats:
    if max(d[k]+c[k] for _,d,c in cols) < 1024: continue
    print(k.ljust(14)+"".join(("%.1f / %.1f" % (d[k]/1024,c[k]/1024)).rjust(26) for _,d,c in cols))
print("TOTAL".ljust(14)+"".join(("%.1f / %.1f" % (sum(d.values())/1024,sum(c.values())/1024)).rjust(26) for _,d,c in cols))
