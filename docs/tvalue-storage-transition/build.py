from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent
prior=json.loads(Path('docs/tvalue-materialization-routing/build-validation.json').read_text())
for k,v in prior['overlay_class_sha256'].items():assert hashlib.sha256((Path('../build/tvalue-materialization-routing-classes')/k).read_bytes()).hexdigest()==v
for mode in ('clean','shadow'):
 expected=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text())
 for k,v in expected.items():assert hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v
assert not subprocess.check_output(['git','diff','8ca65c10e6d1cbe3a1cb4f3d46140ea65fe4b187','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
cp='../build/tvalue-materialization-routing-classes:../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-shadow:lib/jline-3.13.0.jar'
out=Path('../build/tvalue-storage-transition-classes');out.mkdir(exist_ok=True)
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(p/'StorageTransitionRunner.java')];subprocess.run(cmd,check=True)
hashes={str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')};assert list(hashes)==['org/kanger/StorageTransitionRunner.class']
(p/'build-validation.json').write_text(json.dumps({'parent':'8ca65c10e6d1cbe3a1cb4f3d46140ea65fe4b187','command':cmd,'only_new_runner':hashes,'all_previous_native_and_hook_classes_unchanged':True,'production_changed':False},indent=2)+'\n');print('STORAGE_TRANSITION_BUILD_OK')
