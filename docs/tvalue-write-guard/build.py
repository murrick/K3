from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='9e4e683f2eb3449c896ccc0a639c1208fcc10205'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
source=Path('../build/tvalue-materialization-routing-source/Base.java').read_text();signature='public void add(IStep one) throws Exception {'
wrapper='''public void add(IStep one) throws Exception {
        Object token=org.kanger.RecordedLinks.beginUpsert(this);
        boolean success=false;
        try{guardedAddBody(one);success=true;}
        finally{org.kanger.RecordedLinks.endUpsert(token,success);}
    }

    private void guardedAddBody(IStep one) throws Exception {'''
assert source.count(signature)==1;modified=source.replace(signature,wrapper);assert modified.replace(wrapper,signature)==source
getwrapper='''public IStep get(long id) throws Exception {
        IStep result=materializationGetBody(id);
        org.kanger.TValueDirtyJournal.materialized(this,result);
        return result;
    }

    private IStep materializationGetBody(long id) throws Exception {'''
assert source.replace(getwrapper,'public IStep get(long id) throws Exception {')==Path('kanger-data-dumb/src/org/kanger/storage/Base.java').read_text()
folder=Path('../build/tvalue-write-guard-source');folder.mkdir(exist_ok=True);(folder/'Base.java').write_text(modified)
native=Path('kanger-data-dumb/src/org/kanger/storage/DumbFaultInjector.java').read_text();hook='\n        org.kanger.StorageFaultRunner.hit(point);';probe=native.replace('static void hit(String point) {','static void hit(String point) {'+hook);assert probe.replace(hook,'')==native;(folder/'DumbFaultInjector.java').write_text(probe)
info={'parent':parent,'prior_materialization_Base_recovered':True,'original_native_Base_recovered':True,'original_native_halt_path_recovered':True,'Data_decode_journal_factory_unchanged':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 for stage in ('tvalue-saved-links','tvalue-update-boundary','tvalue-storage-faults'):
  hashes=json.loads(Path('docs',stage,'build-validation.json').read_text())['builds'][mode]['overlay_class_sha256'];assert all(hashlib.sha256((Path('../build',stage+'-'+mode)/k).read_bytes()).hexdigest()==v for k,v in hashes.items())
 prior=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v for k,v in prior.items())
 cp=':'.join(['../build/tvalue-saved-links-'+mode,'../build/tvalue-update-boundary-'+mode,'../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'lib/jline-3.13.0.jar'])
 out=Path('../build/tvalue-write-guard-'+mode);out.mkdir(exist_ok=True)
 sources=[folder/'Base.java',folder/'DumbFaultInjector.java',*[p/(n+'.java') for n in ('RecordedLinks','ResidentPersistentRead','PersistentSettlementRunner','SavedLinksRunner','UpdateBoundaryRunner','StorageFaultRunner')]]
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),*map(str,sources)];subprocess.run(cmd,check=True)
 info['builds'][mode]={'command':cmd,'prior_native_classes_verified':len(prior),'overlay_class_sha256':{str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')}}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('WRITE_GUARD_BUILD_OK')
