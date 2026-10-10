from pathlib import Path
import json,shutil,subprocess,gzip
p=Path(__file__).parent
root=Path('../build/tvalue-journal-context-snapshots-wiring-');clean=Path('../build/tvalue-observer-fast-disabled-clean');hooked=Path('../build/tvalue-observer-fast-disabled-hooked')
for name in ('vanilla','missing-mind','missing-factory','missing-value'):
 out=Path(str(root)+name)
 if out.exists():shutil.rmtree(out)
 if name=='vanilla':
  shutil.copytree(clean,out)
  for q in out.glob('org/kanger/TValueObserv*.class'):q.unlink()
 else:
  out.mkdir(parents=True);klass={'missing-mind':'org/kanger/Mind.class','missing-factory':'org/kanger/factory/TValueFactory.class','missing-value':'org/kanger/units/TValue.class'}[name]
  target=out/klass;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(clean/klass,target)
 cp='../build/tvalue-observer-fast-disabled-tests:../build/tvalue-journal-context-snapshots.jar:'+str(out)+(':'+str(hooked) if name!='vanilla' else '')+':lib/jline-3.13.0.jar'
 cmd=['java','-cp',cp,'org.kanger.VanillaConsumerRunner'];r=subprocess.run(cmd,capture_output=True,timeout=30)
 (p/(name+'.wiring.json')).write_text(json.dumps({'exit_code':r.returncode,'command':cmd},indent=2)+'\n');(p/(name+'.log.gz')).write_bytes(gzip.compress(r.stdout,mtime=0));(p/(name+'.err')).write_bytes(r.stderr)
 assert r.returncode==0 and not r.stderr and b'VANILLA_CONSUMER_REFUSAL_OK' in r.stdout,(name,r.stdout,r.stderr)
 print('WIRING_REFUSAL_OK',name,flush=True)
