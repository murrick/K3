#!/usr/bin/env python3
"""Audit the stage-656 native block report, exact baseline and recorded fault boundaries."""
import argparse
import ast,hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('report',type=Path)
args=parser.parse_args();RUN=args.report.resolve().parent
d=json.loads(args.report.read_text());assert d['prepared_inputs']['java_profile']=='baseline'
assert all(d['wire_controls'].values()) and all(d['cut_controls'].values())
count=0;summary={}
def cold(result):
 assert result['cold_read']['observed'] and result['cold_read']['journal_recovery_seen']
 for phase in ['write','cold_read']:assert result[phase]['exit_code']==-9
for op,revisions in d['operations'].items():
 previous=revisions['previous'];next_revision=revisions['next'];seed=d['seeds'][op];witness=d['witnesses'][op]
 cold(seed);cold(witness['result']);assert seed['selected_revision']==previous and witness['result']['selected_revision']==next_revision
 assert seed['write']['observed_event']=='previous_publication_ack' and not seed['write']['next_application_ack_observed']
 assert witness['result']['write']['next_application_ack_observed']
 n=witness['flushes'];cases=d['cases'][op]
 expected={'f'+str(i)+'-'+e for i in range(1,n+1) for e in ['a','b']}|{'w1-a'}
 assert set(cases)==expected
 for name,c in cases.items():
  cold(c);w=c['write'];fault=w['cut'];case=RUN/op/name
  assert w['observed_event']=='block_cut' and w['cut_reached'] and w['previous_ack_stats'] is not None
  assert w['expected_marker'] is None and not w['next_application_ack_observed']
  assert c['selected_revision'] in (previous,next_revision) and c['previous_ack_and_history_preserved']
  serial=(case/('write-resume-native-'+op+'-serial.log')).read_text()
  assert serial.count('KANGER_BLOCK_PREVIOUS_ACK operation='+op+' previous='+str(previous))==1
  assert 'KANGER_BLOCK_NEXT_ACK' not in serial
  es=[json.loads(line) for line in (case/('write-resume-native-'+op+'-block.jsonl')).read_text().splitlines()]
  armed=[x for x in es if x['event']=='fault_armed'];reached=[x for x in es if x['event']=='fault_reached']
  assert len(armed)==len(reached)==1
  assert all(reached[0][key]==fault[key] for key in ['command','ordinal','edge'])
  assert reached[0]['seen']==fault['ordinal']
  assert es[-2]['event']=='fault_reached' and es[-1]['event']=='power_cut'
  assert w['post_previous_ack_stats']['flushes']>=fault['ordinal']-(fault['edge']=='before') if fault['command']==3 else True
  count+=1
 summary[op]=dict(cases=len(cases),old=sum(c['selected_revision']==previous for c in cases.values()),new=sum(c['selected_revision']==next_revision for c in cases.values()),flushes=n)
if set(d['operations'])=={'repin','disconnect'}:
 assert d['checked_topology_control'] is None
else:
 assert set(d['operations'])=={'topology','reindex'}
 cold(d['checked_topology_control']);assert d['checked_topology_control']['selected_revision']==2
for name,c in d['append_helper_regression'].items():
 cold(c);assert c['selected_revision'] in (1,2)
 if name=='ack':assert c['selected_revision']==2 and c['write']['next_application_ack_observed']
 else:assert c['write']['cut_reached'] and not c['write']['next_application_ack_observed']
for name,digest in {**d['artifacts_sha256'],**d['prepared_inputs']['sha256']}.items():assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==digest,name
jar=ROOT/'kanger-console/target/kanger-console-3.3-SNAPSHOT.jar'
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='ca5cce279f6e7a9e36760ae4c3545902b205b677585fa38cacde348d88225f23'
with zipfile.ZipFile(jar) as z:
 names=[n for n in z.namelist() if n.startswith('org/kanger/storage/dumb2/') and n.endswith('.class')]
 assert len(names)==175 and all(z.read(n)==(ROOT/'kanger-data-dumb2/target/classes'/n).read_bytes() for n in names)
classes=list((ROOT/'target/persistence-vm/rootfs/app/classes').rglob('*.class'));assert len(classes)==1 and classes[0].name=='ContextBlockDeviceRunner.class'
for name in ['scripts/qualify-context-native-block-device.py','scripts/qualify-context-block-device.py','scripts/persistence_vm/prepare.py']:ast.parse((ROOT/name).read_text())
boots=len(list(RUN.rglob('*-command.json')))
print('NATIVE_CUT_ACK_LEDGER_HISTORY_HASH_JAR_AST_OK cases='+str(count)+' boots='+str(boots))
print(json.dumps(summary,indent=2))
