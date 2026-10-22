from statistics import median
WINDOW=16
FIELDS=['query_wall_ns']+['q'+str(q) for q in range(7)]
def medians(rows,a,b):return {key:median(r[key] for r in rows[a:b]) for key in FIELDS}
def compare(before,after):
 ratios={key:after[key]/before[key] for key in FIELDS}
 return {'passed':all(1/(1.05 if key=='query_wall_ns' else 1.10)<=value<=(1.05 if key=='query_wall_ns' else 1.10) for key,value in ratios.items()),'ratios':ratios}
def check_gate(rows,cutoff):
 windows=[medians(rows,a,a+WINDOW) for a in range(cutoff-4*WINDOW,cutoff,WINDOW)]
 comparisons=[compare(windows[i],windows[i+1]) for i in range(3)]
 return {'passed':all(c['passed'] for c in comparisons),'comparisons':comparisons}
def select_pair(baseline,candidate):
 for cutoff in range(64,209,WINDOW):
  gates=[check_gate(rows,cutoff) for rows in (baseline,candidate)]
  if not all(g['passed'] for g in gates):continue
  holdouts=[compare(medians(rows,cutoff,cutoff+16),medians(rows,cutoff+32,cutoff+48)) for rows in (baseline,candidate)]
  return {'status':'stable' if all(h['passed'] for h in holdouts) else 'holdout_unstable','cutoff':cutoff,'end':cutoff+48,'gates':gates,'holdouts':holdouts}
 return {'status':'no_joint_gate','cutoff':None,'end':None}
