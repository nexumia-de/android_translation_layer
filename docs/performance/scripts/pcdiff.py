import sys,gzip,re,collections
def load(p):
    d=collections.Counter(); name=None
    for l in gzip.open(p,'rt',errors='replace'):
        m=re.match(r'^[0-9a-f]+-[0-9a-f]+ \S+ \S+ \S+ \S+ \S*\s*(.*)$',l)
        if m: name=m.group(1) or '[anon]'; continue
        if l.startswith('Private_Clean:'): d[name]+=int(l.split()[1])
    return d
a=load(sys.argv[1]); b=load(sys.argv[2])
rows=sorted(set(a)|set(b),key=lambda k:-(abs(a[k]-b[k])))
for k in rows[:int(sys.argv[3]) if len(sys.argv)>3 else 8]: print('%8.1f %8.1f  %s'%(a[k]/1024,b[k]/1024,k[-90:]))
