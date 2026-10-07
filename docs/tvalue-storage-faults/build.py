from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='79b5fe1da42a523d5f56c8b32101ff32248a0064'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
s=Path('kanger-data-dumb/src/org/kanger/storage/DumbFaultInjector.java').read_text();hook='\n        org.kanger.StorageFaultRunner.hit(point);'
needle='static void hit(String point) {';assert s.count(needle)==1
probe=s.replace(needle,needle+hook);assert probe.replace(hook,'')==s
folder=Path('../build/tvalue-storage-faults-source');folder.mkdir(exist_ok=True);(folder/'DumbFaultInjector.java').write_text(probe)
info={'parent':parent,'original_native_halt_path_recovered':True,'throw_probe_only':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 for stage in ('tvalue-saved-links','tvalue-update-boundary'):
  old=json.loads(Path('docs',stage,'build-validation.json').read_text())['builds'][mode]['overlay_class_sha256'];assert all(hashlib.sha256((Path('../build',stage+'-'+mode)/k).read_bytes()).hexdigest()==v for k,v in old.items())
 prior=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v for k,v in prior.items())
 cp=':'.join(['../build/tvalue-saved-links-'+mode,'../build/tvalue-update-boundary-'+mode,'../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'lib/jline-3.13.0.jar'])
 out=Path('../build/tvalue-storage-faults-'+mode);out.mkdir(exist_ok=True)
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(folder/'DumbFaultInjector.java'),str(p/'StorageFaultRunner.java')];subprocess.run(cmd,check=True)
 hashes={str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')};assert all(k.startswith(('org/kanger/StorageFaultRunner','org/kanger/storage/DumbFaultInjector')) for k in hashes)
 info['builds'][mode]={'command':cmd,'overlay_class_sha256':hashes,'prior_native_class_count':len(prior)}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('STORAGE_FAULT_BUILD_OK')
