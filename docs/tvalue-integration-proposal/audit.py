from pathlib import Path
import json,hashlib,subprocess,re
p=Path(__file__).parent
stages=['tvalue-authority-stream','tvalue-authority-contexts','tvalue-parent-add','tvalue-smart-base','tvalue-promotion-retire','tvalue-mind-settlement','tvalue-native-hooks','tvalue-native-mutations','tvalue-recycled-id','tvalue-consumer-gate']
records=[];checked=0
for stage in stages:
 root=Path('docs')/stage;summary=root/'summary.json';manifest=root/'evidence-sha256.json';s=json.loads(summary.read_text());entries=json.loads(manifest.read_text())
 for name,digest in entries.items():
  assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==digest,(stage,name);checked+=1
 assert s['production_changed'] is False
 records.append({'stage':stage,'report':f'docs/{stage}.md','summary':str(summary),'summary_sha256':hashlib.sha256(summary.read_bytes()).hexdigest(),'manifest':str(manifest),'manifest_sha256':hashlib.sha256(manifest.read_bytes()).hexdigest(),'verified_manifest_entries':len(entries),'final_JVMs':s['final_JVMs'],'develop_head':s.get('develop_head'),'report_commit':subprocess.check_output(['git','log','-1','--format=%H','--',f'docs/{stage}.md'],text=True).strip()})
core='5d5f6aff271f1abf371fbde55d637744c53f0bc3';assert all(x['develop_head']==core for x in records[3:])
consumer=json.loads(Path('docs/tvalue-consumer-gate/summary.json').read_text());assert consumer['qualified_exports']==63 and consumer['recycled_identity_refusals']==27 and consumer['additional_missing_event_adapter_and_no_session_refusals']==27
assert consumer['SMART_backend_persistence_qualified'] is False
proposal=Path('docs/tvalue-integration-proposal.md');assert proposal.exists()
assert not subprocess.check_output(['git','diff','d74b4af0b880cad3b43709b1e7328fbf3685b14f','--','kanger/src','kanger-qualification/src','kanger-data-dumb/src','.github'])
result={'parent':'d74b4af0b880cad3b43709b1e7328fbf3685b14f','exact_qualified_native_head':core,'proposal_is_documentation_only':True,'prior_stages_audited':len(records),'manifest_entries_verified':checked,'new_runtime_JVMs':0,'new_performance_measurements':0,'production_integration_performed':False,'develop_merged':False,'recommendation':'scoped diagnostic integration only; production activation blocked pending native identity policy, source requalification, complete corpus and persistence qualification','stages':records}
(p/'audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
for link in re.findall(r'\]\(([^)]+)\)',proposal.read_text()):
 if not link.startswith(('https:','#')):assert (proposal.parent/link).exists(),link
print('INTEGRATION_PROPOSAL_AUDIT_OK stages='+str(len(records))+' manifest_entries='+str(checked)+' new_JVMs=0')
