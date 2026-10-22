from pathlib import Path
import subprocess,json,gzip,tempfile,shutil,hashlib
p=Path(__file__).parent
source=(p/'ResidentWarmupRunner.java').read_text().replace('ResidentWarmupRunner','ResidentCompilationRunner')
source=source.replace('User u=(User)', '''long nanoBefore=System.nanoTime();long uptime=ManagementFactory.getRuntimeMXBean().getUptime();long nanoAfter=System.nanoTime();
  Files.write(Paths.get(target+".clock.json"),Collections.singletonList("{\\"nano_before\\":"+nanoBefore+",\\"uptime_ms\\":"+uptime+",\\"nano_after\\":"+nanoAfter+"}"));
  User u=(User)''')
(p/'ResidentCompilationRunner.java').write_text(source)
out=Path('../build/tvalue-resident-compilation-tests');out.mkdir(exist_ok=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/tvalue-journal-touch-strings.jar:../build/tvalue-observer-fast-disabled-hooked:lib/jline-3.13.0.jar','-d',str(out),str(p/'ResidentCompilationRunner.java')],check=True)
for profile in ('default','batch'):
 for mode in ('baseline','candidate'):
  label=f'{profile}-off-0-{mode}-jit';f=p/(label+'.result.json');r=json.loads(f.read_text());assert not (p/(label+'.clock.json')).exists(),'Use a fresh evidence directory for diagnostic reruns';archive=p/'pre-alignment';archive.mkdir(exist_ok=True)
  for prior in p.glob(label+'.*'):shutil.copy2(prior,archive/prior.name)
  cmd=r['command'];cmd=[x.replace('tvalue-resident-warmup-tests','tvalue-resident-compilation-tests').replace('org.kanger.ResidentWarmupRunner','org.kanger.ResidentCompilationRunner') for x in cmd]
  home=tempfile.mkdtemp(prefix='compilation-study-');cmd=[('-Duser.home='+home) if x.startswith('-Duser.home=') else x for x in cmd]
  try:result=subprocess.run(cmd,capture_output=True,timeout=180)
  finally:shutil.rmtree(home)
  assert result.returncode==0 and not result.stderr,(label,result.stdout[-1000:],result.stderr)
  (p/(label+'.log.gz')).write_bytes(gzip.compress(result.stdout,mtime=0));(p/(label+'.err')).write_bytes(result.stderr)
  r.update(command=cmd,exit_code=result.returncode,clock_alignment=True);f.write_text(json.dumps(r,indent=2)+'\n')
  for trace in p.glob(label+'.*.trace'):
   Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
  xml=p/(label+'.jit.xml');Path(str(xml)+'.gz').write_bytes(gzip.compress(xml.read_bytes(),mtime=0));xml.unlink()
  print('DIAGNOSTIC_OK',label,flush=True)
(p/'diagnostic-build.json').write_text(json.dumps({'class_sha256':{str(x):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')},'source_sha256':hashlib.sha256((p/'ResidentCompilationRunner.java').read_bytes()).hexdigest()},indent=2)+'\n')
