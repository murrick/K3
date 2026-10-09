from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;out=Path('../build/tvalue-layer-cost');out.mkdir(parents=True,exist_ok=True)
paths=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
helpers=['docs/tvalue-persistent-lookup/ResidentTValueRead.java','docs/tvalue-write-guard/ResidentPersistentRead.java','docs/tvalue-clear-close-guard/RecordedLinks.java','docs/tvalue-observation-layers/ObservationLayers.java',str(p/'LayerCostRunner.java')]
listing=p/'sources.txt';listing.write_text('\n'.join(paths+helpers)+'\n')
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',str(out),'@'+str(listing)];subprocess.run(cmd,check=True)
prior=json.loads(Path('docs/tvalue-owner-pure-observation/clean-class-sha256.json').read_text());native={str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')};matched={k:v for k,v in native.items() if k in prior and not k.startswith(('org/kanger/ResidentTValueRead','org/kanger/ResidentPersistentRead'))};assert all(prior[k]==v for k,v in matched.items())
info={'parent':'80ea72adbd17dac2324eb444cea0888938d01b77','command':cmd,'native_classes_matching_prior_clean':len(matched),'class_sha256':native,'source_sha256':{x:hashlib.sha256(Path(x).read_bytes()).hexdigest() for x in paths+helpers},'scope':'clean native memory, direct bucket comparison, no journal or storage wrappers','compiler_sha256':hashlib.sha256(Path('../tooling/ecj.jar').read_bytes()).hexdigest()};(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('LAYER_COST_BUILD_OK',len(matched))
