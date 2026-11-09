import sys, gzip, re, collections
# sum Private_Dirty / Private_Clean / Rss (kB) per mapping name of a raw smaps dump
def load(p):
    d=collections.defaultdict(lambda:[0,0,0,0]); name=None
    for l in gzip.open(p,'rt',errors='replace'):
        if re.match(r'^[0-9a-f]+-[0-9a-f]+ ',l):
            parts=l.split(None,5); name=parts[5].strip() if len(parts)>5 else '[anon]'
            name=re.sub(r'.*/','',name) or '[anon]'; d[name][3]+=1
        elif l.startswith('Private_Dirty:'): d[name][0]+=int(l.split()[1])
        elif l.startswith('Private_Clean:'): d[name][1]+=int(l.split()[1])
        elif l.startswith('Rss:'): d[name][2]+=int(l.split()[1])
    return d
a=load(sys.argv[1]); b=load(sys.argv[2])
tot=lambda d,i: sum(v[i] for v in d.values())
print("total private_dirty %d -> %d kB, private_clean %d -> %d kB, rss %d -> %d kB"%(tot(a,0),tot(b,0),tot(a,1),tot(b,1),tot(a,2),tot(b,2)))
rows=sorted(set(a)|set(b), key=lambda k: -abs(b[k][0]-a[k][0]))
for k in rows[:int(sys.argv[3]) if len(sys.argv)>3 else 15]:
    print("%7d %7d %+7d  dirty | clean %6d %6d | %s"%(a[k][0],b[k][0],b[k][0]-a[k][0],a[k][1],b[k][1],k[:70]))
