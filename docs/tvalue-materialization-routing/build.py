from pathlib import Path
import subprocess,json,hashlib
root=Path(__file__).parent;parent='50550406ae1761322d06dcb995e2bd4c00fdb78a'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
source=Path('kanger-data-dumb/src/org/kanger/storage/Base.java').read_text()
sig='    public IStep get(long id) throws Exception {'
wrapper='''    public IStep get(long id) throws Exception {
        IStep result=materializationGetBody(id);
        org.kanger.TValueDirtyJournal.materialized(this,result);
        return result;
    }

'''
body=sig.replace('public IStep get(','private IStep materializationGetBody(')
assert source.count(sig)==1
changed=source.replace(sig,wrapper+body)
assert changed.replace(wrapper,'').replace(body,sig)==source
temp=Path('../build/tvalue-materialization-routing-source/Base.java');temp.parent.mkdir(parents=True,exist_ok=True);temp.write_text(changed)
prior=json.loads(Path('docs/tvalue-persistent-lookup/build-validation.json').read_text())
assert all(hashlib.sha256((Path('../build/tvalue-persistent-lookup-classes')/k).read_bytes()).hexdigest()==v for k,v in prior['overlay_class_sha256'].items())
cp='../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-shadow:lib/jline-3.13.0.jar'
out=Path('../build/tvalue-materialization-routing-classes');out.mkdir(parents=True,exist_ok=True)
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(temp),str(root/'TValueDirtyJournal.java'),str(root/'MaterializationRoutingRunner.java'),str(root/'MaterializationScopeRunner.java')]
subprocess.run(cmd,check=True)
for build in ('clean','shadow'):
    classes=Path('../build/tvalue-owner-pure-observation-'+build);expected=json.loads(Path('docs/tvalue-owner-pure-observation',build+'-class-sha256.json').read_text())
    assert all(hashlib.sha256((classes/k).read_bytes()).hexdigest()==v for k,v in expected.items())
hashes={str(p.relative_to(out)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.rglob('*.class'))}
assert all(k.startswith(('org/kanger/storage/Base','org/kanger/TValueDirtyJournal','org/kanger/MaterializationRoutingRunner','org/kanger/MaterializationScopeRunner')) for k in hashes)
journal=(root/'TValueDirtyJournal.java').read_text();start=journal.index('    /** Post-successful native Base.get only.');end=journal.index('    public static void promoted(',start)
recovered=(journal[:start]+journal[end:]).replace('\nimport org.kanger.storage.*;\nimport org.kanger.interfaces.internal.IStep;','')
assert recovered==Path('docs/tvalue-owner-pure-observation/TValueDirtyJournal.java').read_text()
info={'parent':parent,'command':cmd,'original_Base_get_body_recovered_byte_for_byte':True,'original_journal_methods_recovered_byte_for_byte':True,'native_source_sha256':hashlib.sha256(source.encode()).hexdigest(),'temporary_Base_sha256':hashlib.sha256(changed.encode()).hexdigest(),'overlay_class_sha256':hashes,'prior_696_classes_each_build_unchanged':True,'production_changed':False}
(root/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n')
print('MATERIALIZATION_ROUTING_BUILD_OK')
