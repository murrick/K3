from pathlib import Path
import difflib, hashlib, json, subprocess

root=Path(__file__).parent
base='1c943d02d39bd33ebd91fabb7c4190112ab49459'
assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-udf/src','kanger-qualification/src','.github'])
sources=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
temporary=Path('../build/resolved-candidate-source');temporary.mkdir(exist_ok=True,parents=True)
patch=[]
def replace(s,a,b):
    assert s.count(a)==1,(a,s.count(a))
    return s.replace(a,b)
def method(s,start,end,fn):
    a=s.index(start);b=s.index(end,a+len(start))
    return s[:a]+fn(s[a:b])+s[b:]
for path in sources.copy():
    original=Path(path).read_text();s=original
    if path.endswith('/Linker.java'):
        def select(b):
            b=replace(b,' throws Exception {',' throws Exception {\n        org.kanger.ResolvedCandidateProfile.Frame profile = org.kanger.ResolvedCandidateProfile.select();\n        try {')
            b=replace(b,'if (tree.size() != 1) {','if (tree.size() != 1) {\n            org.kanger.ResolvedCandidateProfile.add(1, 1);')
            b=replace(b,'if (candidates == null || candidates.isEmpty()) {','if (candidates == null || candidates.isEmpty()) {\n            org.kanger.ResolvedCandidateProfile.add(2, 1);')
            b=replace(b,'        List<IRule> resolved =','        org.kanger.ResolvedCandidateProfile.bucket(candidates.size(), slave);\n        List<IRule> resolved =')
            b=replace(b,'if (resolved.isEmpty()) {','if (resolved.isEmpty()) {\n            org.kanger.ResolvedCandidateProfile.add(9, 1);')
            b=replace(b,'allowedIds.add(candidate.getId())','allowedIds.add(org.kanger.ResolvedCandidateProfile.allowed(candidate.getId()))')
            b=replace(b,'allowedIds.contains(candidate.getId())','allowedIds.contains(org.kanger.ResolvedCandidateProfile.bucketId(candidate.getId()))')
            b=replace(b,'                filtered.add(candidate);','                filtered.add(candidate);\n                org.kanger.ResolvedCandidateProfile.filtered();')
            b=b.replace('return Collections.emptyList();','org.kanger.ResolvedCandidateProfile.selected();\n            return Collections.emptyList();')
            return replace(b,'        return filtered;\n    }','        org.kanger.ResolvedCandidateProfile.selected();\n        return filtered;\n        } finally { org.kanger.ResolvedCandidateProfile.end(profile); }\n    }')
        s=method(s,'    private Collection<IRule> selectDomainCandidates(','    private void addOppositeNatives(',select)
        s=method(s,'    private boolean linkDomains(','    private ',lambda b: b.replace('for (IRule rule : ruleList) {','for (IRule rule : ruleList) {\n                    org.kanger.ResolvedCandidateProfile.add(31, 1);').replace('statistics.incrementDomainPairs();','statistics.incrementDomainPairs();\n                                org.kanger.ResolvedCandidateProfile.add(32, 1);').replace('long operationId = statistics.incrementUnificationAttempts(', 'org.kanger.ResolvedCandidateProfile.add(33, 1);\n                                long operationId = statistics.incrementUnificationAttempts('))
    if path.endswith('/RuleFactory.java'):
        def find(b):
            b=replace(b,' throws Exception {',' throws Exception {\n        org.kanger.ResolvedCandidateProfile.add(3, 1);\n        if (getClass() == RuleFactory.class) org.kanger.ResolvedCandidateProfile.add(28, 1);\n        boolean completed = false;\n        try {')
            b=replace(b,'        List<IRule> result =','        org.kanger.ResolvedCandidateProfile.ids(ids.size());\n        List<IRule> result =')
            b=replace(b,'IRule rule = get(id);','IRule rule = org.kanger.ResolvedCandidateProfile.lookup(this, id, 1);')
            b=replace(b,'                result.add(rule);','                result.add(rule);\n                org.kanger.ResolvedCandidateProfile.returned();')
            b=replace(b,'            }\n        }\n        return result;','            } else if (rule != null) {\n                org.kanger.ResolvedCandidateProfile.add(7, 1);\n            }\n        }\n        completed = true;\n        return result;')
            return replace(b,'\n    }','\n        } finally { if (!completed) org.kanger.ResolvedCandidateProfile.add(30, 1); }\n    }')
        s=method(s,'    public List<IRule> findByResolvedDomain(','    public boolean hasActiveRuleWithTerm(',find)
        s=method(s,'    private void collectResolvedCandidateIds(','    /**',lambda b:replace(replace(b,' throws Exception {',' throws Exception {\n        org.kanger.ResolvedCandidateProfile.enterCollect();\n        try {'),'\n    }','\n        } finally { org.kanger.ResolvedCandidateProfile.leaveCollect(); }\n    }'))
    if path.endswith('/RuleCandidateIndex.java'):
        s=replace(s,'activeMind.getRules().get(id)','org.kanger.ResolvedCandidateProfile.lookup(activeMind.getRules(), id, 2)')
        def local(b):
            b=replace(b,'        SignatureKey signature =','        org.kanger.ResolvedCandidateProfile.add(14, 1);\n        SignatureKey signature =')
            b=replace(b,'            selected = signatures.get(signature);','            selected = signatures.get(signature);\n            org.kanger.ResolvedCandidateProfile.add(15, selected.size());')
            b=replace(b,'        if (batchEligible) {\n            boolean cached','        org.kanger.ResolvedCandidateProfile.add(16, selected.size());\n        if (batchEligible) {\n            org.kanger.ResolvedCandidateProfile.add(17, 1);\n            org.kanger.ResolvedCandidateProfile.add(summary == null ? 19 : 18, 1);\n            boolean cached')
            return replace(b,'            selected.removeAll(summary.batchedIds);','            int beforeBatch = selected.size();\n            selected.removeAll(summary.batchedIds);\n            org.kanger.ResolvedCandidateProfile.add(22, beforeBatch - selected.size());')
        s=method(s,'    void collectResolvedLocal(','    private Long resolvedTermId(',local)
    for filename,kind in [('Rule.java',0),('Domain.java',1),('TVariable.java',2)]:
        if path.endswith('/'+filename):
            typename=filename[:-5]
            signature='    public '+typename+' setMind(Mind mind)'+(' throws Exception' if kind<2 else '')+' {'
            s=replace(s,signature,signature+'\n        org.kanger.ResolvedCandidateProfile.binding('+str(kind)+');')
    if s!=original:
        dest=temporary/Path(path).name;dest.write_text(s);sources[sources.index(path)]=str(dest)
        patch+=list(difflib.unified_diff(original.splitlines(True),s.splitlines(True),fromfile='a/'+path,tofile='b/'+path,n=0))
(root/'instrumentation.patch').write_text(''.join(patch))
sources.append(str(root/'ResolvedCandidateProfile.java'))
(root/'sources.txt').write_text('\n'.join(sources)+'\n')
runner=Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text().replace('public final class SonProfileRunner','public final class ResolvedCandidateProfileRunner')
runner=replace(runner,'            try { mind.optimizeHypothesis(); }','            ResolvedCandidateProfile.begin();\n            try { mind.optimizeHypothesis(); }')
runner=replace(runner,'            long optimizeNs = System.nanoTime() - start;','            ResolvedCandidateProfile.finish(i);\n            long optimizeNs = System.nanoTime() - start;')
(root/'ResolvedCandidateProfileRunner.java').write_text(runner)
for mode in ['reference','classes']:
    classes='../build/resolved-candidate-'+mode
    listing=root/'sources.txt' if mode=='classes' else Path('docs/resident-base-comparison-evidence/sources.txt')
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(listing)],check=True)
    runnerpath=root/'ResolvedCandidateProfileRunner.java' if mode=='classes' else Path('kanger-qualification/src/org/kanger/SonProfileRunner.java')
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-d',classes,str(runnerpath)],check=True)
    hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path(classes).rglob('*.class'))}
    (root/(mode+'-class-sha256.json')).write_text(json.dumps(hashes,indent=2)+'\n')
print('RESOLVED_CANDIDATE_BUILD_READY')
