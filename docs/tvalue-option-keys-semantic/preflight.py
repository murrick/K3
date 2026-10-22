from pathlib import Path
import hashlib,json,subprocess,zipfile
p=Path(__file__).parent;parent=Path('docs/tvalue-option-keys')
def sha(f):
 q=Path(f)
 if q.is_absolute():
  try:q=Path.cwd()/q.relative_to('/workspace/K3')
  except ValueError:pass
 return hashlib.sha256(q.read_bytes()).hexdigest()
manifest=json.loads((parent/'manifest.json').read_text())
for f,h in manifest['files'].items():assert sha(f)==h,f
build=json.loads((parent/'build-validation.json').read_text());verified={}
assert sha('../tooling/ecj.jar')==build['compiler_sha256']
for mode,record in build['compilations'].items():
 root=Path('../build/option-keys')/mode
 actual={str(f.relative_to(root)):sha(f) for f in root.rglob('*.class')};assert actual==record['classes'],mode
 for f,h in record['sources'].items():assert sha(f)==h,f
 verified[mode]={'classes':len(actual),'sources':len(record['sources'])}
for mode in ('baseline','candidate'):
 with zipfile.ZipFile(Path('../build/option-keys')/(mode+'.jar')) as z:
  assert {f:hashlib.sha256(z.read(f)).hexdigest() for f in z.namelist()}==build['compilations'][mode]['classes']
runtime=subprocess.run(['java','-version'],capture_output=True,text=True).stderr
assert runtime==json.loads((parent/'toolchain.json').read_text())['runtime']
repeat=Path('docs/tvalue-option-keys-repeat/manifest.json')
for f,h in json.loads(repeat.read_text())['files'].items():assert sha(f)==h,f
(p/'preflight.json').write_text(json.dumps({'parent':'8d7a41ef0e8511ce964969cdcfe0bbab15cb9b37','build_manifest_sha256':sha(parent/'build-validation.json'),'previous_manifest_sha256':sha(parent/'manifest.json'),'native_current':build['current'],'native_old':build['old'],'runtime':runtime,'verified':verified,'previous_repeat_manifest_sha256':sha(repeat),'reused_same_cloud_build':True,'all_saved_source_class_and_jar_hashes_match':True},indent=2)+'\n')
print('SEMANTIC_PREFLIGHT_OK')
