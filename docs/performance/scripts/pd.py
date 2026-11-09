import sys, gzip
# Private_Dirty, Private_Clean, Shared_Clean, Pss, Rss totals (MB) of a raw smaps dump (smaps-*.txt.gz)
t = {'Private_Dirty': 0, 'Private_Clean': 0, 'Shared_Clean': 0, 'Pss': 0, 'Rss': 0}
for l in gzip.open(sys.argv[1], 'rt', errors='replace'):
    k = l.split(':')[0]
    if k in t and l.rstrip().endswith('kB'):
        t[k] += int(l.split()[1])
print(' '.join('%s=%.1f' % (k, v / 1024) for k, v in t.items()))
