from pathlib import Path
import subprocess,tempfile,json,hashlib
p=Path(__file__).parent
parent='c4c42b94626549bfcdce7254e77df9a7863b244d'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
out=Path('../build/tvalue-resident-endpoints-classes');out.mkdir(exist_ok=True)
reader=Path('../build/tvalue-resident-persistent-classes')
old=json.loads(Path('docs/tvalue-resident-persistent/summary.json').read_text())['reader_class_sha256']
assert all(hashlib.sha256((reader/k).read_bytes()).hexdigest()==v for k,v in old.items())
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(reader)+':../build/tvalue-owner-pure-observation-clean:lib/jline-3.13.0.jar','-d',str(out),str(p/'ResidentEndpointsRunner.java')]
subprocess.run(cmd,check=True)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
results=[]
for mode in ('clean','shadow'):
 prior=Path('../build/tvalue-owner-pure-observation-'+mode)
 expected=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text())
 assert all(hashlib.sha256((prior/k).read_bytes()).hexdigest()==v for k,v in expected.items())
 for flag in ('on','off','verify'):
  opts=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
  if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
  label=mode+'-'+flag
  command=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='resident-endpoints-jvm-'),*opts,'-cp',str(out)+':'+str(reader)+':'+str(prior)+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.ResidentEndpointsRunner']
  r=subprocess.run(command,capture_output=True,timeout=60)
  (p/(label+'.log')).write_bytes(r.stdout);(p/(label+'.err')).write_bytes(r.stderr)
  result={'mode':mode,'flags':flag,'command':command,'exit_code':r.returncode,'prior_classes_verified':len(expected)}
  (p/(label+'.result.json')).write_text(json.dumps(result,indent=2)+'\n')
  assert r.returncode==0 and not r.stderr and b'RESIDENT_ENDPOINTS_OK' in r.stdout,(label,r.stdout.decode(),r.stderr.decode())
  results.append(result);print('DONE',label,flush=True)
assert len({(p/(m+'-'+f+'.log')).read_bytes() for m in ('clean','shadow') for f in ('on','off','verify')})==1
summary={'parent':parent,'fresh_JVMs':6,'native_stdout_identical':True,'topology_candidate_rejected':True,'strict_reader_unchanged':True,'journal_unchanged':True,'production_changed':False,'final_composite_publication_qualified':False,'prior_rejected_concurrent_corpus_still_unqualified':True,'command':cmd,'class_sha256':{str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')},'prior_reader_class_sha256':old}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('RESIDENT_ENDPOINTS_MATRIX_OK JVMs=6')
