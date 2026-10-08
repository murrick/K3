from pathlib import Path
import subprocess,hashlib,json
p=Path(__file__).parent
before=Path('../build/tvalue-clear-close-guard-source/Base.java').read_text();needle='                    base.add(stored);';hook='\n                    org.kanger.ReindexRunner.copied(this,base,stored);\n                    org.kanger.DBReindexRunner.copied(this,base,stored);';assert before.count(needle)==1
s=before.replace(needle,needle+hook);assert s.replace(hook,'')==before
folder=Path('../build/tvalue-reindex-boundary-source');folder.mkdir(exist_ok=True);(folder/'Base.java').write_text(s)
info={'parent':'c64805f7535dcc384439e935605fea1e1075784e','native_reindex_body_recovered':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 stages=['tvalue-clear-close-guard','tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary']
 for stage in stages:
  hashes=json.loads(Path('docs',stage,'build-validation.json').read_text())['builds'][mode]['overlay_class_sha256'];assert all(hashlib.sha256((Path('../build',stage+'-'+mode)/k).read_bytes()).hexdigest()==v for k,v in hashes.items())
 native=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v for k,v in native.items())
 cp=':'.join(['../build/'+stage+'-'+mode for stage in stages]+['../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'lib/jline-3.13.0.jar']);out=Path('../build/tvalue-reindex-boundary-'+mode);out.mkdir(exist_ok=True)
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(folder/'Base.java'),str(p/'ReindexRunner.java'),str(p/'DBReindexRunner.java')];subprocess.run(cmd,check=True)
 info['builds'][mode]={'command':cmd,'prior_native_classes_verified':len(native),'overlay_class_sha256':{str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')}}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('REINDEX_BUILD_OK')
