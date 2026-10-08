from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='f6ac32d2e86129d39b646e09a39d8a19889f7d4f'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','.github'])
s=Path('../build/tvalue-delete-flush-guard-source/Base.java').read_text();before=s
changes=[]
for operation,exception in [('clear','Exception'),('close','IOException')]:
 signature='public void '+operation+'() throws '+exception+' {'
 declaration='private void guarded'+operation.title()+'Body() throws '+exception+' {'
 wrapper=signature+'\n        Object token=org.kanger.RecordedLinks.beginMutation(this,"'+operation+'");\n        boolean success=false;\n        try{guarded'+operation.title()+'Body();success=true;}\n        finally{org.kanger.RecordedLinks.endMutation(token,success); }\n    }\n\n    '+declaration
 start=s.index(signature);depth=1;end=start+len(signature)
 while depth:
  depth+=(s[end]=='{')-(s[end]=='}');end+=1
 body=s[start:end];new=body.replace(signature,wrapper).replace('flush();','native'+operation.title()+'Flush();')
 phases= [('data.clear();','data'),('index.clear();','index'),('integrity.clear();','integrity'),('clearCache();','cache'),('lastId = 0;','last-id'),('invalidateEndpoints();','endpoints')] if operation=='clear' else [('integrity.compact();','compact'),('clearCache();','cache'),('index.close();','index'),('data.close();','data'),('invalidateEndpoints();','endpoints')]
 for statement,phase in phases:new=new.replace(statement,statement+'\n            org.kanger.ClearCloseRunner.hit("'+operation+'-after-'+phase+'");')
 s=s[:start]+new+s[end:];changes.append((new,body))
helpers=''
for operation in ('clear','close'):
 helpers+='\n    private void native'+operation.title()+'Flush() throws Exception {\n        Object token=org.kanger.RecordedLinks.beginNativeFlush(this,"'+operation+'");\n        boolean success=false;\n        try{guardedFlushBody();success=true;}\n        finally{org.kanger.RecordedLinks.endMutation(token,success); }\n    }\n'
s=s.replace('    public boolean isClosed() {',helpers+'    public boolean isClosed() {')
recovered=s.replace(helpers,'')
for new,body in reversed(changes):recovered=recovered.replace(new,body)
assert recovered==before
folder=Path('../build/tvalue-clear-close-guard-source');folder.mkdir(exist_ok=True);(folder/'Base.java').write_text(s)
source=Path('kanger-data-dumb/src/org/kanger/storage/DumbFaultInjector.java').read_text();hook='\n        org.kanger.StorageFaultRunner.hit(point);\n        org.kanger.DeleteFlushRunner.hit(point);\n        org.kanger.ClearCloseRunner.hit(point);';probe=source.replace('static void hit(String point) {','static void hit(String point) {'+hook);assert probe.replace(hook,'')==source;(folder/'DumbFaultInjector.java').write_text(probe)
info={'parent':parent,'original_clear_close_bodies_recovered':True,'prior_delete_flush_upsert_get_wrappers_unchanged':True,'native_halt_path_recovered':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 for stage in ('tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary'):
  hashes=json.loads(Path('docs',stage,'build-validation.json').read_text())['builds'][mode]['overlay_class_sha256'];assert all(hashlib.sha256((Path('../build',stage+'-'+mode)/k).read_bytes()).hexdigest()==v for k,v in hashes.items())
 prior=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v for k,v in prior.items())
 cp=':'.join(['../build/tvalue-delete-flush-guard-'+mode,'../build/tvalue-write-guard-'+mode,'../build/tvalue-saved-links-'+mode,'../build/tvalue-update-boundary-'+mode,'../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'lib/jline-3.13.0.jar']);out=Path('../build/tvalue-clear-close-guard-'+mode);out.mkdir(exist_ok=True)
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(folder/'Base.java'),str(folder/'DumbFaultInjector.java'),str(p/'RecordedLinks.java'),str(p/'ClearCloseRunner.java')];subprocess.run(cmd,check=True)
 info['builds'][mode]={'command':cmd,'prior_native_classes_verified':len(prior),'overlay_class_sha256':{str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')}}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('CLEAR_CLOSE_GUARD_BUILD_OK')
