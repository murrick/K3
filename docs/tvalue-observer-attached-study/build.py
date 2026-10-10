from pathlib import Path
import subprocess, shutil, re, json, hashlib
p=Path(__file__).parent
source=Path('kanger-qualification/diagnostics/runtime/org/kanger/TValueObservation.java').read_text()
prefix=source[:source.index('    private interface Callback')]
methods=[]
pattern=r'    public static (void|Object) (\w+)\((.*?)\) \{ if\(current==null\)return(?: null)?; (.*?) \}'
for result,name,args,body in re.findall(pattern,source):
 call=re.search(r'o\.(\w+)\((.*?)\)',body)
 assert call and call[1]==name
 empty='return null;' if result=='Object' else 'return;'
 invoke='return a.observer.'+name+'('+call[2]+');' if result=='Object' else 'a.observer.'+name+'('+call[2]+');'
 methods.append(f'    public static {result} {name}({args}) {{\n        if(current==null){{{empty}}}\n        synchronized(TValueObservation.class) {{\n            Attachment a=current; if(a==null){{{empty}}}\n            if(Thread.currentThread()!=a.owner) {{ a.failed=true; {empty} }}\n            try {{ {invoke} }} catch(Throwable failure) {{ a.failed=true; {empty} }}\n        }}\n    }}')
assert len(methods)==12
out=p/'direct/org/kanger/TValueObservation.java';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(prefix+'\n'.join(methods)+'\n}\n')
target=Path('../build/tvalue-observer-attached-direct')
if target.exists():shutil.rmtree(target)
shutil.copytree('../build/tvalue-observer-fast-disabled-hooked',target)
for f in target.glob('org/kanger/TValueObservation*.class'):f.unlink()
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(target)+':lib/jline-3.13.0.jar','-d',str(target),str(out)]
subprocess.run(cmd,check=True)
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
classes={str(f.relative_to(target)):sha(f) for f in target.rglob('*.class')}
old=json.loads(Path('docs/tvalue-observer-fast-disabled/build-validation.json').read_text())['compilations']['hooked']['class_sha256']
assert all(classes.get(k)==v for k,v in old.items() if not k.startswith('org/kanger/TValueObservation'))
(p/'build-validation.json').write_text(json.dumps({'command':cmd,'source_sha256':sha(out),'class_sha256':classes,'only_bridge_changed':True},indent=2)+'\n')
print('DIRECT_BUILD_OK')
