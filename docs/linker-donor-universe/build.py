from pathlib import Path
import subprocess,json,hashlib
root=Path(__file__).parent;base='1c943d02d39bd33ebd91fabb7c4190112ab49459'
temp=Path('../build/donor-source');temp.mkdir(parents=True,exist_ok=True)
(temp/'Linker.java').write_bytes(subprocess.check_output(['git','show',base+':kanger/src/org/kanger/Linker.java']))
listing=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
runners=['KangerLinkerDonorScopeSafetyRunner','KangerLinkerRuleOrderingSafetyRunner','KangerLinkerCheckpointBalanceSafetyRunner','KangerCompletedHypothesisContractRunner','LatentSolveSyncTransactionRunner','KangerRuleCandidateConcurrencyRunner','LatentSubstitutionCorpusRunner']
for mode in ('reference','split'):
 paths=listing.copy()
 if mode=='reference':paths[paths.index('kanger/src/org/kanger/Linker.java')]=str(temp/'Linker.java')
 sources=root/(mode+'-sources.txt');sources.write_text('\n'.join(paths)+'\n')
 classes='../build/donor-'+mode
 subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(sources)],check=True,timeout=120)
 extra=[Path('kanger-qualification/src/org/kanger/'+r+'.java') for r in runners]+[root/'ExactCandidateReplayRunner.java',root/'FrontierDependencyWitness.java']
 subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes+':lib/jline-3.13.0.jar','-sourcepath','kanger-qualification/src','-d',classes,*map(str,extra)],check=True,timeout=30)
 hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path(classes).rglob('*.class'))};(root/(mode+'-class-sha256.json')).write_text(json.dumps(hashes,indent=2)+'\n')
a=json.loads((root/'reference-class-sha256.json').read_text());b=json.loads((root/'split-class-sha256.json').read_text());basehash=json.loads((root/'base-class-sha256.json').read_text());assert all(a[k]==v for k,v in basehash.items())
changed=[k for k in a if b.get(k)!=a[k]];assert changed and all(k.startswith('org/kanger/Linker') and 'Statistics' not in k for k in changed),changed
(root/'class-comparison.json').write_text(json.dumps({'base':base,'baseline_classes_identical':len(basehash),'changed_shared_classes':changed},indent=2)+'\n')
print('DONOR_BUILD_READY',changed)
runner=Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text().replace('public final class SonProfileRunner','public final class ScopeTraceSonRunner')
runner=runner.replace('        for (int i = 0; i < samples; i++) {','        for (int i = 0; i < samples; i++) {\n            LinkerScopeTrace.begin();')
runner=runner.replace('            LinkerStatistics s = mind.getLinkerStatistics();','            LinkerScopeTrace.finish(i);\n            LinkerStatistics s = mind.getLinkerStatistics();')
(root/'ScopeTraceSonRunner.java').write_text(runner)
for mode in ('reference','split'):
 path=temp/'Linker.java' if mode=='reference' else Path('kanger/src/org/kanger/Linker.java')
 body=path.read_text();a=body.index('    public void link(');b=body.index('    private boolean rotator(',a)
 # The split source includes a rotator Javadoc; keeping it in this region is harmless.
 part=body[a:b];part=part.replace(' throws Exception {',' throws Exception {\n        LinkerScopeTrace.enter(mind,rule);\n        try {',1)
 for label,name in [('descending','leftList'),('ascending','ruleList')]:
  needle='            rotator('+name+(', '+name if mode=='split' else '')+', causes, logging);'
  assert part.count(needle)==1
  part=part.replace(needle,'            LinkerScopeTrace.observe(currentPass,"'+label+'",'+name+','+name+');\n'+needle)
 # Insert finally before link's closing brace, before any following rotator Javadoc.
 end=part.index('    /**') if '    /**' in part else len(part)
 close=part.rfind('\n    }',0,end);assert close>=0;part=part[:close]+'\n        } finally { LinkerScopeTrace.leave(); }'+part[close:]
 body=body[:a]+part+body[b:];target=temp/(mode+'-trace');target.mkdir(exist_ok=True);(target/'Linker.java').write_text(body)
 paths=listing.copy();paths[paths.index('kanger/src/org/kanger/Linker.java')]=str(target/'Linker.java');paths.append(str(root/'LinkerScopeTrace.java'))
 listingpath=root/(mode+'-trace-sources.txt');listingpath.write_text('\n'.join(paths)+'\n');classes='../build/donor-'+mode+'-trace'
 subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(listingpath)],check=True,timeout=120)
 subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-d',classes,str(root/'ScopeTraceSonRunner.java')],check=True,timeout=30)
 hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path(classes).rglob('*.class'))};(root/(mode+'-trace-class-sha256.json')).write_text(json.dumps(hashes,indent=2)+'\n')
print('DONOR_TRACE_BUILD_READY')
