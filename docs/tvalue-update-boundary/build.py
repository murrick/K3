from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='c7319da9c67a0e74169e754e80d00a3e915b6028'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
prior=json.loads(Path('docs/tvalue-materialization-routing/build-validation.json').read_text())
for k,v in prior['overlay_class_sha256'].items():assert hashlib.sha256((Path('../build/tvalue-materialization-routing-classes')/k).read_bytes()).hexdigest()==v
journal=(p/'TValueDirtyJournal.java').read_text();a=journal.index('    private static final class UpdateFrame {');b=journal.index('    private static final class State {',a);recovered=(journal[:a]+journal[b:]).replace('\n        UpdateFrame update;','').replace('\n        if(s.update!=null&&s.update.base==candidate){s.update.pending.put(step.getId(),step);return;}','');assert recovered==Path('docs/tvalue-materialization-routing/TValueDirtyJournal.java').read_text()
sig='    public void update() throws Exception {'
wrapper='''    public void update() throws Exception {
        Object journalUpdate=org.kanger.TValueDirtyJournal.beginUpdate(mind,this);
        boolean journalSuccess=false;
        try {journalUpdateBody();journalSuccess=true;}
        finally {org.kanger.TValueDirtyJournal.endUpdate(journalUpdate,journalSuccess);}
    }

'''
body=sig.replace('public void update(','private void journalUpdateBody(');info={'parent':parent,'original_update_body_recovered':True,'all_old_journal_methods_recovered':True,'prior_696_classes_each_mode_unchanged':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 old=Path('kanger/src/org/kanger/factory/TValueFactory.java') if mode=='clean' else Path('../build/tvalue-owner-pure-observation-shadow-source/factory/TValueFactory.java');source=old.read_text();assert source.count(sig)==1;changed=source.replace(sig,wrapper+body);assert changed.replace(wrapper,'').replace(body,sig)==source
 temp=Path('../build/tvalue-update-boundary-'+mode+'-source/TValueFactory.java');temp.parent.mkdir(parents=True,exist_ok=True);temp.write_text(changed)
 native=Path('../build/tvalue-owner-pure-observation-'+mode);expected=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((native/k).read_bytes()).hexdigest()==v for k,v in expected.items())
 cp='../build/tvalue-materialization-routing-classes:../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:'+str(native)+':lib/jline-3.13.0.jar';out=Path('../build/tvalue-update-boundary-'+mode);out.mkdir(exist_ok=True)
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(temp),str(p/'TValueDirtyJournal.java'),str(p/'UpdateBoundaryRunner.java')];subprocess.run(cmd,check=True)
 hashes={str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')};assert all(k.startswith(('org/kanger/factory/TValueFactory','org/kanger/TValueDirtyJournal','org/kanger/UpdateBoundaryRunner')) for k in hashes)
 info['builds'][mode]={'command':cmd,'source_before_sha256':hashlib.sha256(source.encode()).hexdigest(),'temporary_source_sha256':hashlib.sha256(changed.encode()).hexdigest(),'overlay_class_sha256':hashes}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('UPDATE_BOUNDARY_BUILD_OK')
