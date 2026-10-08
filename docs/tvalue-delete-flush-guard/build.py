from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='82cf4a2cad984d62bea22187031c4b6701085828'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','.github'])
s=Path('../build/tvalue-write-guard-source/Base.java').read_text();before=s
wrappers=[]
for signature,name,args,operation in [('public void flush() throws Exception {','guardedFlushBody','','flush'),('public void deleteAll(Collection<Long> ids) throws Exception {','guardedDeleteAllBody','ids','delete')]:
 declaration=signature.replace('public void','private void').replace('flush()',name+'()') if not args else 'private void '+name+'(Collection<Long> ids) throws Exception {'
 wrapper=signature+'\n        Object token=org.kanger.RecordedLinks.beginMutation(this,"'+operation+'");\n        boolean success=false;\n        try{'+name+'('+args+');success=true;}\n        finally{org.kanger.RecordedLinks.endMutation(token,success);}\n    }\n\n    '+declaration
 assert s.count(signature)==1;s=s.replace(signature,wrapper);wrappers.append((wrapper,signature))
recovered=s
for w,signature in reversed(wrappers):recovered=recovered.replace(w,signature)
assert recovered==before
folder=Path('../build/tvalue-delete-flush-guard-source');folder.mkdir(exist_ok=True);(folder/'Base.java').write_text(s)
source=Path('kanger-data-dumb/src/org/kanger/storage/DumbFaultInjector.java').read_text();hook='\n        org.kanger.StorageFaultRunner.hit(point);\n        org.kanger.DeleteFlushRunner.hit(point);';probe=source.replace('static void hit(String point) {','static void hit(String point) {'+hook);assert probe.replace(hook,'')==source;(folder/'DumbFaultInjector.java').write_text(probe)
info={'parent':parent,'original_delete_flush_bodies_recovered':True,'prior_upsert_and_get_wrappers_unchanged':True,'native_halt_path_recovered':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 for stage in ('tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary'):
  hashes=json.loads(Path('docs',stage,'build-validation.json').read_text())['builds'][mode]['overlay_class_sha256'];assert all(hashlib.sha256((Path('../build',stage+'-'+mode)/k).read_bytes()).hexdigest()==v for k,v in hashes.items())
 prior=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v for k,v in prior.items())
 cp=':'.join(['../build/tvalue-write-guard-'+mode,'../build/tvalue-saved-links-'+mode,'../build/tvalue-update-boundary-'+mode,'../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'lib/jline-3.13.0.jar']);out=Path('../build/tvalue-delete-flush-guard-'+mode);out.mkdir(exist_ok=True)
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(folder/'Base.java'),str(folder/'DumbFaultInjector.java'),str(p/'RecordedLinks.java'),str(p/'DeleteFlushRunner.java')];subprocess.run(cmd,check=True)
 info['builds'][mode]={'command':cmd,'prior_native_classes_verified':len(prior),'overlay_class_sha256':{str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')}}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('DELETE_FLUSH_GUARD_BUILD_OK')
