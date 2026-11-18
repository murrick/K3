#!/usr/bin/env python3
"""Read-only audit of distinct-device pin receipts, including unresolved windows."""
import argparse,ast,hashlib,json,zipfile,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('report',type=Path);args=p.parse_args()
d=json.loads(args.report.read_text());run=args.report.resolve().parent
assert d['prepared_inputs']['java_profile']=='baseline'
assert d['production_code_changed'] is False and d['positive_consumers_unmodified']
assert all(d['wire_controls'].values())
uuids=[]
for name in ['producer','consumer']:
 image=ROOT/d['prepared_inputs']['data_templates'][name]
 header=subprocess.check_output(['dumpe2fs','-h',str(image)],stderr=subprocess.DEVNULL,text=True)
 uuids.append(re.search(r'^Filesystem UUID:\s+(\S+)',header,re.M).group(1))
assert len(set(uuids))==2

expected={m+'-'+k for m in ['append','reindex','create','fork'] for k in ['export','window']}|{'append-bypass','append-lying'}
assert set(d['cases'])==expected
failures=[]
for name,c in d['cases'].items():
 mode,kind=name.split('-');w=c['write'];r=c['cold_read'];negative=kind in ('bypass','lying')
 assert w['exit_code']==r['exit_code']==-9
 assert w['failure'] is None and w['visible_stats'] is not None
 assert w['marker'].startswith(('PIN_VISIBLE' if kind=='window' else 'PIN_CONSUMER_ACK')+' mode='+mode+' ')
 assert w['producer_only_cut']==(kind=='window')
 assert w['consumer_provider_unmodified']==(kind!='bypass') and r['consumer_provider_unmodified']
 assert set(w['block_stats'])=={'os','producer','consumer'}
 assert all(r['honour_barriers'].values())
 assert w['honour_barriers']==dict(os=True,producer=kind!='lying',consumer=True)
 barriers=w['post_visible_stats']['producer']['flushes']+w['post_visible_stats']['producer']['fua']
 if kind=='export':assert barriers>0
 if kind=='bypass':assert barriers==0
 assert r['journal_recovery']['vdb'] or kind=='lying'
 if kind!='window':assert r['journal_recovery']['vdc']
 write=(run/name/(('write-negative' if kind=='bypass' else 'write')+'-'+mode+'-serial.log')).read_text()
 assert write.count('PIN_VISIBLE mode='+mode+' ')==1
 assert ('PIN_CONSUMER_ACK' in write)==(kind!='window')
 assert 'PIN_PRODUCER_ACK_UNEXPECTED' not in write
 for phase in ['write-negative' if kind=='bypass' else 'write','read-producer' if kind=='window' else ('read-missing' if negative else 'read')]:
  commands=json.loads((run/name/(phase+'-'+mode+'-command.json')).read_text())
  nbd=[v for v in commands if v.startswith('driver=nbd,')];assert len(nbd)==3 and len(set(nbd))==3
  for device in ['os','producer','consumer']:
   ledger=[json.loads(v) for v in (run/name/(phase+'-'+mode+'-'+device+'.jsonl')).read_text().splitlines()]
   assert ledger[-1]['event']=='power_cut'
 if c['passed']:
  assert r['failure'] is None
  assert r['marker'].startswith(('PIN_PRODUCER_ONLY_RECOVERED' if kind=='window' else ('PIN_COLD_MISSING' if negative else 'PIN_COLD_RECOVERED'))+' mode='+mode+' ')
 else:
  assert kind=='window' and mode in ('create','fork') and r['marker'] is None and r['failure']
  serial=(run/name/('read-producer-'+mode+'-serial.log')).read_text()
  assert 'Incomplete DUMB2 Context metadata at /producer/Producer: both .context and .revision sidecars are required' in serial
  assert 'ContextSnapshot.requireContextMetadata' in serial and 'KANGER_GUEST_FAILED' in serial
  failures.append(name)
assert d['passed']==(not failures)
for path,digest in {**d['artifacts_sha256'],**d['prepared_inputs']['sha256']}.items():
 path=Path(path);assert hashlib.sha256((path if path.is_absolute() else ROOT/path).read_bytes()).hexdigest()==digest,str(path)
jar=ROOT/'kanger-console/target/kanger-console-3.3-SNAPSHOT.jar'
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='ca5cce279f6e7a9e36760ae4c3545902b205b677585fa38cacde348d88225f23'
with zipfile.ZipFile(jar) as z:
 classes=[n for n in z.namelist() if n.startswith('org/kanger/storage/dumb2/') and n.endswith('.class')]
 assert len(classes)==175 and all(z.read(n)==(ROOT/'kanger-data-dumb2/target/classes'/n).read_bytes() for n in classes)
root=ROOT/'target/persistence-pin-vm/rootfs/app'
assert {c.name for c in (root/'classes').rglob('*.class')}=={'ContextBlockDeviceRunner.class','ContextDevicePinRunner.class','DevicePinPause.class'}
assert {c.name for c in (root/'producer-overlay').rglob('*.class')}=={'RevisionStore.class'}
assert {c.name for c in (root/'consumer-negative-overlay').rglob('*.class')}=={'DurablePins.class'}
original=(ROOT/'kanger-data-dumb2/src/org/kanger/storage/dumb2/RevisionStore.java').read_text()
injected=(ROOT/'target/persistence-pin-vm/RevisionStore.java').read_text()
assert injected.count('            DevicePinPause.visible(path);\n')==2
assert injected.replace('            DevicePinPause.visible(path);\n','')==original
init=(root.parent/'sbin/init').read_text()
assert 'data=ordered,barrier=1,commit=3600 /dev/vdb /producer' in init
assert 'data=ordered,barrier=1,commit=3600 /dev/vdc /consumer' in init
assert 'if [ "$action" = write-negative ]; then cp=/app/consumer-negative-overlay:$cp; fi' in init
for path in ['scripts/qualify-context-device-pins.py','scripts/persistence_vm/prepare_pins.py',__file__]:ast.parse(Path(path).read_text())
assert len(list(run.rglob('*-command.json')))==20
print('DEVICE_PIN_EVIDENCE_AUDIT_OK cases=10 boots=20 original_provider_classes=175')
print('Qualification:', 'PASS' if d['passed'] else 'INCOMPLETE; unresolved windows='+','.join(failures))
