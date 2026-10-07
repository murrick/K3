from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='f6ef43a7348bf270ae196db389c033306f305702'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
info={'parent':parent,'only_new_runner':True,'recorder_reader_Data_journal_factory_unchanged':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 for stage,folder in [('tvalue-saved-links','tvalue-saved-links-'+mode),('tvalue-update-boundary','tvalue-update-boundary-'+mode)]:
  hashes=json.loads(Path('docs',stage,'build-validation.json').read_text())['builds'][mode]['overlay_class_sha256']
  assert all(hashlib.sha256((Path('../build',folder)/k).read_bytes()).hexdigest()==v for k,v in hashes.items())
 prior=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v for k,v in prior.items())
 cp=':'.join(['../build/tvalue-saved-links-'+mode,'../build/tvalue-update-boundary-'+mode,'../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'lib/jline-3.13.0.jar'])
 out=Path('../build/tvalue-saved-link-failure-'+mode);out.mkdir(exist_ok=True)
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(p/'UpdateBoundaryRunner.java')];subprocess.run(cmd,check=True)
 hashes={str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')};assert all(k.startswith('org/kanger/UpdateBoundaryRunner') for k in hashes)
 info['builds'][mode]={'command':cmd,'overlay_class_sha256':hashes,'prior_native_class_count':len(prior)}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('SAVED_LINK_FAILURE_BUILD_OK')
