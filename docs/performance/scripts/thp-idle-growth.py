# Anonymous memory and AnonHugePages at start and end of idle runs (run directories with smaps-start/end.txt.gz).
import sys, gzip, re
def tot(p):
    t={"Private_Dirty":0,"AnonHugePages":0,"Swap":0,"Anonymous":0}
    for line in gzip.open(p,'rt',errors='replace'):
        k,_,v=line.partition(":")
        if k in t and v.strip().endswith("kB"): t[k]+=int(v.split()[0])
    return t
print("%-40s %8s %8s %8s %8s   (MB: anon start, anon end, huge start, huge end) growth anon / huge" % ("run","anonS","anonE","hugeS","hugeE"))
for d in sys.argv[1:]:
    try: s=tot(d+"/smaps-start.txt.gz"); e=tot(d+"/smaps-end.txt.gz")
    except Exception as x: continue
    a=lambda t:(t["Anonymous"]+t["Swap"])/1024; h=lambda t:t["AnonHugePages"]/1024
    print("%-40s %8.1f %8.1f %8.1f %8.1f   %+6.1f / %+6.1f" % (d[-40:],a(s),a(e),h(s),h(e),a(e)-a(s),h(e)-h(s)))
