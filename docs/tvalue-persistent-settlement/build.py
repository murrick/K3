from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='c137746803bc94d0441bc97237301e463a5aa3e8'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
old=json.loads(Path('docs/tvalue-update-boundary/build-validation.json').read_text());info={'parent':parent,'only_new_runner':True,'all_prior_runtime_classes_unchanged':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 for k,v in old['builds'][mode]['overlay_class_sha256'].items():assert hashlib.sha256((Path('../build/tvalue-update-boundary-'+mode)/k).read_bytes()).hexdigest()==v
 prior=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v for k,v in prior.items())
 cp='../build/tvalue-update-boundary-'+mode+':../build/tvalue-materialization-routing-classes:../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-'+mode+':lib/jline-3.13.0.jar';out=Path('../build/tvalue-persistent-settlement-'+mode);out.mkdir(exist_ok=True)
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(p/'PersistentSettlementRunner.java')];subprocess.run(cmd,check=True)
 hashes={str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')};assert all(k.startswith('org/kanger/PersistentSettlementRunner') for k in hashes)
 info['builds'][mode]={'command':cmd,'runner_class_sha256':hashes}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('PERSISTENT_SETTLEMENT_BUILD_OK')
