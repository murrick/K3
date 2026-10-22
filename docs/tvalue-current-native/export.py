from pathlib import Path
import subprocess,gzip,json,hashlib,shutil
p=Path(__file__).parent;jfr='/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/jfr';scratch=Path('../build/current-native/private-recordings');scratch.mkdir(parents=True,exist_ok=True);allowed={'org.kanger.ResidentQuery','jdk.ExecutionSample','jdk.ObjectAllocationSample'}
for result in sorted(p.glob('*-profile.result.json')):
 label=result.name.removesuffix('.result.json');raw=p/(label+'.jfr');r=json.loads(result.read_text());body=subprocess.check_output([jfr,'print','--json','--stack-depth','128','--events',','.join(sorted(allowed)),str(raw)]);events=json.loads(body)['recording']['events'];assert set(e['type'] for e in events)<=allowed
 target=p/(label+'.events.json.gz');target.write_bytes(gzip.compress(body,mtime=0));r['raw_recording_sha256']=hashlib.sha256(raw.read_bytes()).hexdigest();r['retained_export_sha256']=hashlib.sha256(target.read_bytes()).hexdigest();r['retained_event_types']=sorted(allowed);result.write_text(json.dumps(r,indent=2)+'\n');shutil.move(str(raw),str(scratch/raw.name));print('EVENT_EXPORT_OK',label,flush=True)
