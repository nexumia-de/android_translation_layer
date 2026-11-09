import sys, gzip, re
# list mappings with Private_Dirty > N kB: start, size, dirty, name
cur=None
for l in gzip.open(sys.argv[1],'rt',errors='replace'):
    m=re.match(r'^([0-9a-f]+)-([0-9a-f]+) \S+ \S+ \S+ \S+\s*(.*)$',l)
    if m: cur=(int(m.group(1),16),int(m.group(2),16),re.sub(r'.*/','',m.group(3)) or '[anon]')
    elif l.startswith('Private_Dirty:') and int(l.split()[1])>=int(sys.argv[2]):
        print("%12x %8d kB size %7d kB dirty %s"%(cur[0],(cur[1]-cur[0])//1024,int(l.split()[1]),cur[2][:60]))
