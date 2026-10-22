from pathlib import Path
import subprocess,json,hashlib,zipfile,shutil
p=Path(__file__).parent; build=Path('../build'); sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
def compile(paths,out,cp):
 out.mkdir(parents=True,exist_ok=True);listing=build/(out.name+'.sources');listing.write_text('\n'.join(paths)+'\n')
 subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),'@'+str(listing)],check=True)
 return {str(x.relative_to(out)):sha(x) for x in out.rglob('*.class')}
assert sha('../tooling/ecj.jar')==json.loads(Path('docs/tvalue-observer-cost/build-validation.json').read_text())['compiler_sha256']
native=Path('docs/tvalue-observer-fast-disabled/native-sources.txt').read_text().splitlines(); runtime=Path('kanger-qualification/diagnostics/runtime-sources.txt').read_text().splitlines();records={}
for mode in ('clean','hooked'):
 paths=native+runtime
 if mode=='hooked':
  paths=[str(Path('docs/tvalue-observer-fast-disabled/source')/x.split('/kanger/src/org/kanger/')[1]) if '/kanger/src/org/kanger/' in x and x.split('/kanger/src/org/kanger/')[1] in ('Mind.java','factory/TValueFactory.java','units/TValue.java') else x for x in paths]
 records[mode]=compile(paths,build/('tvalue-observer-fast-disabled-'+mode),'lib/jline-3.13.0.jar')
 expected=json.loads(Path('docs/tvalue-observer-fast-disabled/build-validation.json').read_text())['compilations'][mode]['class_sha256'];differences=sorted(k for k in records[mode] if records[mode][k]!=expected.get(k));assert set(records[mode])==set(expected);assert differences==['org/mozilla/javascript/NativeArray.class'],differences;records[mode+'_prior_differences']=differences
for mode,ref,name in [('baseline','73c5d6c2','context-snapshots'),('candidate','HEAD','touch-strings')]:
 source=p/mode;paths=Path('kanger-qualification/diagnostics/sources.txt').read_text().splitlines();restored=[]
 for f in paths:
  dest=source/f;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(subprocess.check_output(['git','show',ref+':'+f]));restored.append(str(dest))
 out=build/('warmup-'+mode);records[mode]=compile(restored,out,str(build/'tvalue-observer-fast-disabled-clean')+':lib/jline-3.13.0.jar')
 expected=json.loads(Path('docs/tvalue-journal-'+name+'/build-validation.json').read_text())['class_sha256'];assert records[mode]==expected,mode+' frozen bytecode differs'
 jar=build/('tvalue-journal-'+name+'.jar')
 with zipfile.ZipFile(jar,'w') as z:
  for f in sorted(records[mode]):z.writestr(zipfile.ZipInfo(f,(1980,1,1,0,0,0)),(out/f).read_bytes())
 shutil.rmtree(source)
fixture=Path('docs/tvalue-resident-inference/ResidentInferenceRunner.java').read_text().replace('ResidentInferenceRunner','ResidentWarmupRunner')
fixture=fixture.replace('query_allocated_bytes\");','query_allocated_bytes,start_ns,end_ns\");').replace('queryWall+\",\"+queryBytes);','queryWall+\",\"+queryBytes+\",\"+start+\",\"+(start+elapsed));')
(p/'ResidentWarmupRunner.java').write_text(fixture)
records['fixture']=compile([str(p/'ResidentWarmupRunner.java')],build/'tvalue-resident-warmup-tests',str(build/'tvalue-journal-touch-strings.jar')+':'+str(build/'tvalue-observer-fast-disabled-hooked')+':lib/jline-3.13.0.jar')
(p/'build-validation.json').write_text(json.dumps({'classes':records,'runtime_JDK_difference':'NativeArray.class only; all Kanger classes identical; modules checked separately','compiler_sha256':sha('../tooling/ecj.jar'),'java':subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True)},indent=2)+'\n');print('WARMUP_BUILD_OK')
