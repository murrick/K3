from pathlib import Path
import json,subprocess,hashlib,difflib
root=Path(__file__).parent;base='1c943d02d39bd33ebd91fabb7c4190112ab49459'
assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-udf/src','kanger-qualification/src','.github'])
original=Path('kanger/src/org/kanger/Linker.java').read_text();s=original
helper='LinkerFrontierProfile'
def replace(s,a,b):
 assert s.count(a)==1,(a,s.count(a));return s.replace(a,b)
a=s.index('    public void link(Rule rule, boolean logging) throws Exception {');b=s.index('    private boolean rotator(',a)
section=s[a:b];section=replace(section,' throws Exception {',' throws Exception {\n        Object frontierLink='+helper+'.beginLink(mind,rule);\n        try {')
section=replace(section,'            rotator(leftList, causes, logging);','            '+helper+'.order(currentPass,"descending");\n            rotator(leftList, causes, logging);')
section=replace(section,'            rotator(ruleList, causes, logging);','            '+helper+'.order(currentPass,"ascending");\n            rotator(ruleList, causes, logging);')
i=section.rfind('\n    }');section=section[:i]+'\n        } finally { '+helper+'.endLink(frontierLink); }'+section[i:];s=s[:a]+section+s[b:]
s=replace(s,'            statistics.incrementRuleVisits();','            '+helper+'.Mark frontierRule='+helper+'.start(mind,statistics,causes,r);\n            statistics.incrementRuleVisits();')
s=replace(s,'        }\n\n        return used;','            '+helper+'.end(frontierRule,mind,statistics,causes);\n        }\n\n        return used;')
temp=Path('../build/frontier-source');temp.mkdir(parents=True,exist_ok=True);(temp/'Linker.java').write_text(s)
(root/'instrumentation.patch').write_text(''.join(difflib.unified_diff(original.splitlines(True),s.splitlines(True),fromfile='a/kanger/src/org/kanger/Linker.java',tofile='b/kanger/src/org/kanger/Linker.java',n=3)))
listing=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines();listing[listing.index('kanger/src/org/kanger/Linker.java')]=str(temp/'Linker.java');listing.append(str(root/'LinkerFrontierProfile.java'));(root/'sources.txt').write_text('\n'.join(listing)+'\n')
runner=Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text().replace('public final class SonProfileRunner','public final class LinkerFrontierRunner')
runner=replace(runner,'            try { mind.optimizeHypothesis(); }','            LinkerFrontierProfile.begin();\n            try { mind.optimizeHypothesis(); }')
runner=replace(runner,'            List<String> optimized = texts(mind);','            LinkerFrontierProfile.finish(i);\n            List<String> optimized = texts(mind);');(root/'LinkerFrontierRunner.java').write_text(runner)
for mode in ('reference','profile'):
 classes='../build/frontier-'+mode;source=root/'sources.txt' if mode=='profile' else Path('docs/resident-base-comparison-evidence/sources.txt')
 subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(source)],check=True,timeout=120)
 extras=[root/'FrontierDependencyWitness.java',Path('kanger-qualification/src/org/kanger/KangerCompletedHypothesisContractRunner.java'),root/'ExactCandidateReplayRunner.java']
 extras.append(root/'LinkerFrontierRunner.java' if mode=='profile' else Path('kanger-qualification/src/org/kanger/SonProfileRunner.java'))
 if mode=='profile':extras.append(root/'FrontierCandidateReplayRunner.java')
 subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-d',classes,*map(str,extras)],check=True,timeout=30)
 hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path(classes).rglob('*.class'))};(root/(mode+'-class-sha256.json')).write_text(json.dumps(hashes,indent=2)+'\n')
reference=json.loads((root/'reference-class-sha256.json').read_text());profile=json.loads((root/'profile-class-sha256.json').read_text());changed=[k for k in reference if profile.get(k)!=reference[k]]
assert changed and all(k.startswith('org/kanger/Linker') and not 'Statistics' in k for k in changed),changed
baseline=json.loads((root/'base-class-sha256.json').read_text());assert len(baseline)==651 and all(reference[k]==v for k,v in baseline.items())
(root/'class-comparison.json').write_text(json.dumps({'base':base,'shared_classes':len(set(reference)&set(profile)),'changed':changed},indent=2)+'\n');print('FRONTIER_BUILD_READY',changed)
archive='../build/frontier-witness-census'
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/frontier-reference','-d',archive,str(root/'witness-census/FrontierDependencyWitness.java')],check=True,timeout=30)
sha=hashlib.sha256((Path(archive)/'org/kanger/FrontierDependencyWitness.class').read_bytes()).hexdigest()
for mode in ('reference','profile'):assert json.loads((root/(mode+'-census-class-sha256.json')).read_text())['org/kanger/FrontierDependencyWitness.class']==sha
(root/'witness-census-comparison.json').write_text(json.dumps({'archived_census_witness_classes_identical':True,'sha256':sha},indent=2)+'\n')
print('ARCHIVED_CENSUS_WITNESS_READY identical_class=true')
