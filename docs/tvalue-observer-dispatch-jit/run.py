from pathlib import Path
import subprocess,json,tempfile,gzip,platform,os,shutil
p=Path(__file__).parent
modes=('slow-disabled','slow-attached','fast-disabled','fast-attached','direct-disabled','direct-attached')
orders=[modes[i:]+modes[:i] for i in range(6)];orders += [tuple(reversed(x)) for x in orders]
runtimes={'slow':'tvalue-observer-fast-disabled-slow','fast':'tvalue-observer-fast-disabled-hooked','direct':'tvalue-observer-attached-direct'}
plan=[(rep,mode,False) for rep,order in enumerate(orders) for mode in order]+[(0,mode+'-attached',True) for mode in runtimes]
(p/'environment.json').write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'cpus':os.cpu_count(),'orders':orders,'plan':plan,'shared_host':True,'warmup_batches':64,'measured_batches':64,'calls_per_batch':65536},indent=2)+'\n')
for rep,mode,jit in plan:
 label=('jit-' if jit else '')+f'{rep}-{mode}';target=(p/label).resolve();runtime,attachment=mode.split('-')
 cp='../build/tvalue-observer-dispatch-jit:../build/tvalue-observer-fast-disabled-cost-tests:../build/tvalue-observer-fast-disabled.jar:../build/'+runtimes[runtime]+':../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 home=tempfile.mkdtemp(prefix='dispatch-probe-')
 options=['-XX:+UnlockDiagnosticVMOptions','-XX:+LogCompilation','-XX:LogFile='+str(target)+'.xml','-XX:+PrintInlining'] if jit else []
 cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+home,'-Dresult.path='+str(target),*options,'-cp',cp,'org.kanger.MinimalObserverRunner',attachment]
 print('START',label,flush=True)
 try:r=subprocess.run(cmd,capture_output=True,timeout=180)
 finally:shutil.rmtree(home)
 (p/(label+'.log.gz')).write_bytes(gzip.compress(r.stdout,mtime=0));(p/(label+'.err')).write_bytes(r.stderr)
 (p/(label+'.result.json')).write_text(json.dumps({'mode':mode,'repetition':rep,'jit':jit,'exit_code':r.returncode,'command':cmd},indent=2)+'\n')
 assert r.returncode==0 and not r.stderr and b'MINIMAL_OBSERVER_OK' in r.stdout,(label,r.stdout.decode(),r.stderr.decode())
 if jit:
  xml=p/(label+'.xml');Path(str(xml)+'.gz').write_bytes(gzip.compress(xml.read_bytes(),mtime=0));xml.unlink()
 print('DONE',label,flush=True)
print('DISPATCH_MATRIX_OK',len(plan))
