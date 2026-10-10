from pathlib import Path
import subprocess,gzip,json
p=Path(__file__).parent
for mode in ('slow','guarded','hooked'):
 cp='../build/tvalue-observer-direct-dispatch-contract:../build/tvalue-observer-direct-dispatch-'+mode+':lib/jline-3.13.0.jar'
 cmd=['java','-cp',cp,'org.kanger.ObserverDispatchRunner'];r=subprocess.run(cmd,capture_output=True,timeout=30)
 (p/(mode+'.contract.json')).write_text(json.dumps({'command':cmd,'exit_code':r.returncode},indent=2)+'\n')
 (p/(mode+'.contract.log.gz')).write_bytes(gzip.compress(r.stdout,mtime=0));(p/(mode+'.contract.err')).write_bytes(r.stderr)
 assert r.returncode==0 and not r.stderr and b'OBSERVER_DISPATCH_CONTRACT_OK' in r.stdout,(mode,r.stdout,r.stderr)
 print('CONTRACT_OK',mode,flush=True)
