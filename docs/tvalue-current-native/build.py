from pathlib import Path
import subprocess,json,hashlib,zipfile,shutil
p=Path(__file__).parent;b=Path('../build/current-native');b.mkdir(parents=True,exist_ok=True);sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
assert sha('../tooling/ecj.jar')==json.loads(Path('docs/tvalue-observer-cost/build-validation.json').read_text())['compiler_sha256']
changes=json.loads(Path('docs/tvalue-observer-fast-disabled/changes.json').read_text());runtime=Path('kanger-qualification/diagnostics/runtime-sources.txt').read_text().splitlines();oldpaths=Path('docs/tvalue-observer-fast-disabled/native-sources.txt').read_text().splitlines();records={};proof={}
def compile(paths,out,cp):
 out.mkdir(parents=True,exist_ok=True);listing=b/(out.name+'.sources');listing.write_text('\n'.join(paths)+'\n');cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),'@'+str(listing)];subprocess.run(cmd,check=True)
 return {'command':cmd,'sources':{f:sha(f) for f in paths},'classes':{str(f.relative_to(out)):sha(f) for f in out.rglob('*.class')}}
for base,native in [('old',Path('../K3-observer-native')),('current',Path('../K3-current-native'))]:
 paths=[x.replace('../K3-observer-native',str(native)) for x in oldpaths]
 if base=='current':
  for module in ('kanger','kanger-command','kanger-console'):
   for f in sorted((native/module/'src').rglob('*.java')):
    if str(f) not in paths:paths.append(str(f))
 mapping={};proof[base]={}
 for name,edits in changes.items():
  original=(native/'kanger/src/org/kanger'/name).read_text();body=original
  for edit in edits:assert body.count(edit['old'])==1,(base,name);body=body.replace(edit['old'],edit['new'],1)
  recovered=body
  for edit in reversed(edits):assert recovered.count(edit['new'])==1;recovered=recovered.replace(edit['new'],edit['old'],1)
  assert recovered==original;dest=p/'instrumented'/base/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body);mapping[str(native/'kanger/src/org/kanger'/name)]=str(dest);proof[base][name]={'native_sha256':sha(native/'kanger/src/org/kanger'/name),'hooked_sha256':sha(dest),'reverse_restores_original':True}
 for mode in ('clean','hooked'):records[base+'-'+mode]=compile((paths if mode=='clean' else [mapping.get(x,x) for x in paths])+runtime,b/(base+'-'+mode),'lib/jline-3.13.0.jar')
 clean=records[base+'-clean']['classes'];hooked=records[base+'-hooked']['classes'];assert clean.keys()==hooked.keys();assert all(k.startswith(('org/kanger/Mind','org/kanger/factory/TValueFactory','org/kanger/units/TValue')) for k in clean if clean[k]!=hooked[k])
for mode in ('clean','hooked'):
 expected=json.loads(Path('docs/tvalue-observer-fast-disabled/build-validation.json').read_text())['compilations'][mode]['class_sha256'];actual=records['old-'+mode]['classes'];assert actual.keys()==expected.keys();assert [k for k in actual if actual[k]!=expected[k]]==['org/mozilla/javascript/NativeArray.class']
for mode,ref,name in [('baseline','73c5d6c2','context-snapshots'),('candidate','95f1c4ef','touch-strings')]:
 source=p/'modules'/mode;paths=[]
 for f in Path('kanger-qualification/diagnostics/sources.txt').read_text().splitlines():
  dest=source/f;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(subprocess.check_output(['git','show',ref+':'+f]));paths.append(str(dest))
 records[mode]=compile(paths,b/mode,str(b/'current-clean')+':lib/jline-3.13.0.jar');expected=json.loads(Path('docs/tvalue-journal-'+name+'/build-validation.json').read_text())['class_sha256'];assert records[mode]['classes']==expected
 with zipfile.ZipFile(b/(mode+'.jar'),'w') as z:
  for f in sorted(expected):z.writestr(zipfile.ZipInfo(f,(1980,1,1,0,0,0)),(b/mode/f).read_bytes())
 shutil.rmtree(source)
s=Path('docs/tvalue-resident-stability/ResidentStabilityRunner.java').read_text().replace('ResidentStabilityRunner','ResidentCurrentRunner');s=s.replace('static int checks;', '''@jdk.jfr.Name("org.kanger.ResidentQuery") @jdk.jfr.Label("Resident query interval") @jdk.jfr.StackTrace(false)
 static final class QueryEvent extends jdk.jfr.Event { public int iteration; public int query; }
 static int checks;''');s=s.replace('String mode=args[0],target=','boolean profile=Boolean.getBoolean("resident.profile");\n  String mode=args[0],target=');s=s.replace('Mind m=new Mind(u);','Mind m=new Mind(u);if(Boolean.getBoolean("resident.override"))OptimizationOptions.setAll(m,false);');s=s.replace('long qa=bean.getThreadAllocatedBytes','QueryEvent event=profile?new QueryEvent():null;if(event!=null){event.iteration=i;event.query=q;event.begin();}\n     long qa=bean.getThreadAllocatedBytes');s=s.replace('queryWall+=qElapsed;queryBytes+=qAllocated;','queryWall+=qElapsed;queryBytes+=qAllocated;if(event!=null){event.end();event.commit();}');(p/'ResidentCurrentRunner.java').write_text(s);records['fixture']=compile([str(p/'ResidentCurrentRunner.java')],b/'fixture',str(b/'candidate.jar')+':'+str(b/'current-hooked')+':lib/jline-3.13.0.jar')
(p/'build-validation.json').write_text(json.dumps({'old':'2422f7f6d9e1af7608203b1566df64b5f29fe344','current':'60fdc34736c51fa4e5659982630347fb1d1b59fc','compiler_sha256':sha('../tooling/ecj.jar'),'compilations':records,'hooks':proof},indent=2)+'\n');print('CURRENT_BUILD_OK')
