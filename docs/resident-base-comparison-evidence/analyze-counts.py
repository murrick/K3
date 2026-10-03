from pathlib import Path
import re
folder=Path(__file__).parent
snapshots={'RAW':set(),'OPTIMIZED':set()};data={}
for mode in ['false','true']:
 text=(folder/('counts-'+mode+'.log')).read_text()
 assert not (folder/('counts-'+mode+'.err')).read_text()
 for marker in snapshots:snapshots[marker].update(re.findall('^'+marker+' .*$',text,re.M))
 for sample in range(2):
  data[(mode,sample)]={k:int(v) for k,v in re.findall(r'COUNT sample='+str(sample)+r' (\S+)=(\d+)',text)}
  assert data[(mode,sample)]['ArgumentsList.diagDepth']==0
for sample in range(2):
 off=data[('false',sample)];on=data[('true',sample)]
 for key in ['ArgumentsList.diagCalls','ArgumentsList.diagIterations','Domain.diagSet','TVariable.diagSet']:
  assert off[key]==on[key],(sample,key)
 loops=on['ArgumentsList.diagIterations'];fast=on['ArgumentsList.diagFastEqual']+on['ArgumentsList.diagFastMismatch']
 parent=on['ArgumentsList.diagParentFallback'];unknown=loops-fast-parent
 print('SAMPLE',sample,'positions',loops,'fast',fast,'parent_fallback',parent,'unknown',unknown,'fast_fraction=%.6f'%(fast/loops))
 for key in ['Argument.diagValueInBase','TVariable.diagValueInBase','TValueFactory.diagGetInBase','TValueFactory.diagGet','TVariable.diagActive']:
  print(key,'OFF',off[key],'ON',on[key],'reduction=%.4f%%'%((1-on[key]/off[key])*100))
assert all(len(values)==1 for values in snapshots.values())
print('COUNTER_TRACE_COUNTS_AND_HYPOTHESES_EQUAL samples=4')
