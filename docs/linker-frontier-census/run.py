from pathlib import Path
import subprocess,json,gzip,re,base64,sys,tempfile
root=Path(__file__).parent;expected=json.loads((root/'expected.json').read_text());son=json.loads((root/'son-expected.json').read_text())
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
def execute(label,mode,runner,args=(),samples=False):
 p=root/label;command=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='frontier-jvm-')]+['-Dkanger.experiment.'+f+'=true' for f in flags]+(['-Dbench.samples=3'] if samples else [])+['-cp','../build/frontier-'+mode+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner,*args]
 with p.with_suffix('.log').open('w') as out,p.with_suffix('.err').open('w') as err:subprocess.run(command,stdout=out,stderr=err,check=True,timeout=300)
 assert not p.with_suffix('.err').read_bytes(),label
 data=p.with_suffix('.log').read_bytes();text=data.decode()
 p.with_suffix('.log.gz').write_bytes(gzip.compress(data,mtime=0));p.with_suffix('.log').unlink()
 if samples:
  assert re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$',text,re.M)==['0','1','2']
  for key,value in expected.items():assert re.findall(r'^'+key+r' (.*)$',text,re.M)==[value]*3
 if runner in ('ExactCandidateReplayRunner','FrontierCandidateReplayRunner'):
  rows={}
  for line in text.splitlines():
   if line.startswith('ROW son '):
    kv=dict(f.split('=',1) for f in line.split()[3:]);source=base64.b64decode(kv['source']).decode();assert source not in rows
    rows[source]={k:{'true':True,'false':False,'null':None}[kv[k]] for k in ('compiled','collision','answer','accepted')}
  assert rows==son and 'EXACT_CANDIDATE_REPLAY_OK' in text
 if runner=='KangerCompletedHypothesisContractRunner':assert 'COMPLETED_HYPOTHESIS_CONTRACT_OK' in text
 print('DONE',label,flush=True)
matrix=[('candidate-reference','reference','ExactCandidateReplayRunner',('exact','all'),False),('candidate-profile','profile','FrontierCandidateReplayRunner',('exact','all'),False),('contract-reference','reference','KangerCompletedHypothesisContractRunner',(),False),('contract-profile','profile','KangerCompletedHypothesisContractRunner',(),False),('clean','reference','SonProfileRunner',(),True),('profile-1','profile','LinkerFrontierRunner',(),True),('profile-2','profile','LinkerFrontierRunner',(),True)]
start=int(sys.argv[1]) if len(sys.argv)>1 else 0
for label,mode,runner,args,samples in matrix[start:]:execute(label,mode,runner,args,samples)
