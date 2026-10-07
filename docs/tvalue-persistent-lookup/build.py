from pathlib import Path
import subprocess,json,hashlib
root=Path(__file__).parent
parent='7bb814c706e790c825fb0a69b1e01084c27a8e1e'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
prior={}
for build in ('clean','shadow'):
    classes=Path('../build/tvalue-owner-pure-observation-'+build)
    expected=json.loads(Path('docs/tvalue-owner-pure-observation',build+'-class-sha256.json').read_text())
    assert all(hashlib.sha256((classes/k).read_bytes()).hexdigest()==v for k,v in expected.items())
    prior[build]={'all_prior_class_hashes_verified':True,'count':len(expected)}
extra=Path('../build/tvalue-resident-persistent-classes')
hashes=json.loads(Path('docs/tvalue-resident-persistent/summary.json').read_text())['reader_class_sha256']
assert all(hashlib.sha256((extra/k).read_bytes()).hexdigest()==v for k,v in hashes.items())
classes=Path('../build/tvalue-persistent-lookup-classes');classes.mkdir(parents=True,exist_ok=True)
sources=[root/'ResidentTValueRead.java',root/'PersistentLookupRunner.java']
command=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/tvalue-owner-pure-observation-clean:'+str(extra)+':lib/jline-3.13.0.jar','-d',str(classes),*map(str,sources)]
subprocess.run(command,check=True)
current={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(classes.rglob('*.class'))}
assert all(k.startswith(('org/kanger/ResidentTValueRead','org/kanger/PersistentLookupRunner')) for k in current)
old=json.loads(Path('docs/tvalue-owner-pure-observation/clean-class-sha256.json').read_text())
changes={k:{'before':old[k],'after':v} for k,v in current.items() if k in old and old[k]!=v}
assert set(changes)=={'org/kanger/ResidentTValueRead.class'}
source_paths=[Path('kanger/src/org/kanger')/s for s in ['Mind.java','User.java','factory/TValueFactory.java','storage/Escalera.java','storage/Sapato.java','units/TValue.java']]+[Path('kanger-data-dumb/src/org/kanger/storage')/s for s in ['Base.java','Data.java','DB.java','IntegrityManifest.java']]
info={'parent':parent,'command':command,'prior_builds':prior,'preceding_persistent_helpers_identical':True,'overlay_class_sha256':current,'changed_preceding_diagnostic_classes':changes,'native_source_sha256':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in source_paths},'production_source_changed':False,'native_instrumentation_changed':False}
(root/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n')
print('PERSISTENT_LOOKUP_BUILD_OK',json.dumps(prior))
