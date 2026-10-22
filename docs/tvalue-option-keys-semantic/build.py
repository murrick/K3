from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;n=Path('../K3-current-native');b=Path('../build/option-keys-semantic');b.mkdir(parents=True,exist_ok=True)
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
assert sha('../tooling/ecj.jar')==json.loads(Path('docs/tvalue-option-keys/build-validation.json').read_text())['compiler_sha256']
names=['LatentSubstitutionCorpusRunner','KangerOptionsRunner','LatentSolveSyncSafetyRunner','LatentSolveSyncTransactionRunner','CauseWeightFallbackRunner','CauseWeightShadowSafetyRunner','SingleTValueLookupRunner','ResidentBaseComparisonRunner','CompactFindSnapshotsRunner','TValueIndexPreservationRunner','TValueIndexBoundaryRunner','TValueIndexReopenRunner']
paths=[str(f) for f in sorted((n/'kanger-data-dumb2/src').rglob('*.java'))]+[str(f) for f in sorted((n/'kanger-qualification/src/org/kanger/test').glob('*.java'))]+[str(n/'kanger-qualification/src/org/kanger'/(x+'.java')) for x in names]
for f in paths:assert Path(f).read_bytes()==subprocess.check_output(['git','show','60fdc34736c51fa4e5659982630347fb1d1b59fc:'+str(Path(f).relative_to(n))]),f
listing=b/'sources.txt';listing.write_text('\n'.join(paths)+'\n');out=b/'classes';out.mkdir(exist_ok=True)
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/option-keys/current-clean:lib/jline-3.13.0.jar','-d',str(out),'@'+str(listing)];subprocess.run(cmd,check=True)
record={'native':'60fdc34736c51fa4e5659982630347fb1d1b59fc','sources_match_exact_native':True,'compiler_sha256':sha('../tooling/ecj.jar'),'command':cmd,'sources':{f:sha(f) for f in paths},'classes':{str(f.relative_to(out)):sha(f) for f in out.rglob('*.class')}}
launcher=b/'launcher';launcher.mkdir(exist_ok=True)
launch_cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/option-keys/current-clean','-d',str(launcher),str(p/'SemanticLaunch.java')];subprocess.run(launch_cmd,check=True)
record['launcher']={'command':launch_cmd,'sources':{str(p/'SemanticLaunch.java'):sha(p/'SemanticLaunch.java')},'classes':{str(f.relative_to(launcher)):sha(f) for f in launcher.rglob('*.class')}}
reference=json.loads(Path('docs/tvalue-option-keys/build-validation.json').read_text())['compilations']['current-clean']['classes']
common=sorted(record['classes'].keys() & reference.keys());assert all(record['classes'][f]==reference[f] for f in common)
record['matching_frozen_corpus_classes']=common
(p/'build-validation.json').write_text(json.dumps(record,indent=2)+'\n');print('SEMANTIC_BUILD_OK',len(paths),len(record['classes']))
