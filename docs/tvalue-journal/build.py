from pathlib import Path
import subprocess,json,hashlib,re
root=Path(__file__).parent;base='e636d1957a6841fab4f2c23f6bb399b8cfd1f848'
temp=Path('../build/tvalue-journal-source');temp.mkdir(parents=True,exist_ok=True)
sources={name:Path('kanger/src/org/kanger/'+name).read_text() for name in ['Mind.java','Linker.java','factory/TValueFactory.java']}
changed=dict(sources)
# Construction baselines only after all native factory initialization succeeds.
mind=changed['Mind.java']
mind=mind.replace('        init();\n    }','        init();\n        TValueStateJournal.constructed(this);\n    }',1)
needle='            if (!initialized) {\n                parent.abortTransactionStart();\n            }\n        }\n    }'
assert mind.count(needle)==1;mind=mind.replace(needle,needle[:-5]+'        TValueStateJournal.constructed(this);\n    }')
# Wrap the existing settlement body without changing its decisions/catch paths.
sig='    private boolean commit(IMind m, boolean settleRejectedChild) throws Exception {'
assert mind.count(sig)==1
wrapper='''    private boolean commit(IMind m, boolean settleRejectedChild) throws Exception {
        TValueStateJournal.beginSettlement(this);
        boolean returned=false,accepted=false;
        try {accepted=journalCommitBody(m,settleRejectedChild);returned=true;return accepted;}
        finally {TValueStateJournal.endSettlement(this);if(!returned||accepted||settleRejectedChild)TValueStateJournal.retire((Mind)m);}
    }

'''
mind=mind.replace(sig,wrapper+sig.replace('commit(','journalCommitBody('))
sig='    public void release(IMind m) throws Exception {'
assert mind.count(sig)==1
wrapper='''    public void release(IMind m) throws Exception {
        TValueStateJournal.beginSettlement(this);
        try {journalReleaseBody(m);}
        finally {TValueStateJournal.endSettlement(this);TValueStateJournal.retire((Mind)m);}
    }

'''
mind=mind.replace(sig,wrapper+sig.replace('public void release(','private void journalReleaseBody('))
changed['Mind.java']=mind
link=changed['Linker.java'];needle='    public void link(Rule rule, boolean logging) throws Exception {'
assert link.count(needle)==1;link=link.replace(needle,needle+'\n        TValueStateJournal.linkStart(mind);')
for name,order in [('leftList','descending'),('ruleList','ascending')]:
    needle='            rotator('+name+', '+name+', causes, logging);';assert link.count(needle)==1
    link=link.replace(needle,needle+'\n            TValueStateJournal.observe(mind,"pass-"+currentPass+"-'+order+'");')
changed['Linker.java']=link
factory=changed['factory/TValueFactory.java']
needle='            indexInitialized = base != null;\n        }\n    }';assert factory.count(needle)==1
factory=factory.replace(needle,'            indexInitialized = base != null;\n        }\n        org.kanger.TValueStateJournal.reset(mind);\n    }')
needle='        return cache.mark();';assert factory.count(needle)==1
factory=factory.replace(needle,'        long journalMark=cache.mark();\n        org.kanger.TValueStateJournal.mark(mind);\n        return journalMark;')
for a,b in [('    public long commit()','    public long release()'),('    public long release()','    public TValue set(')]:
    start=factory.index(a);end=factory.index(b,start);part=factory[start:end];assert part.count('        return result;')==1
    part=part.replace('        return result;','        org.kanger.TValueStateJournal.complete(mind);\n        return result;');factory=factory[:start]+part+factory[end:]
changed['factory/TValueFactory.java']=factory
for name,body in changed.items():
    path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body)
# Evidence source copies are generated from unchanged committed production.
(root/'instrumentation.json').write_text(json.dumps({'base':base,'production_source_sha256':{n:hashlib.sha256(s.encode()).hexdigest() for n,s in sources.items()},'temporary_source_sha256':{n:hashlib.sha256(s.encode()).hexdigest() for n,s in changed.items()},'scope':'temporary wrappers/hooks only; original settlement bodies, inference kernels and factory algorithms retained'},indent=2)+'\n')
listing=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
runners=['KangerLinkerDonorScopeSafetyRunner','KangerLinkerRuleOrderingSafetyRunner','KangerLinkerCheckpointBalanceSafetyRunner','KangerCompletedHypothesisContractRunner','LatentSolveSyncTransactionRunner','KangerRuleCandidateConcurrencyRunner','LatentSubstitutionCorpusRunner','KangerMindCommitExceptionAtomicitySafetyRunner']
for mode in ('clean','journal'):
    paths=listing.copy();classes='../build/tvalue-journal-'+mode
    if mode=='journal':
        for name in changed:paths[paths.index('kanger/src/org/kanger/'+name)]=str(temp/name)
    paths.extend([str(root/'TValueStateJournal.java'),str(root/'JournalRunner.java'),str(root/'TValueJournalSafetyRunner.java')])
    path=root/(mode+'-sources.txt');path.write_text('\n'.join(paths)+'\n')
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@'+str(path)],check=True,timeout=120)
    extras=[Path('kanger-qualification/src/org/kanger/'+r+'.java') for r in runners]+[Path('docs/linker-donor-universe/ExactCandidateReplayRunner.java'),Path('docs/linker-donor-universe/FrontierDependencyWitness.java'),Path('docs/linker-dependency-lifecycle/DependencyLifecycleWitness.java')]
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes+':lib/jline-3.13.0.jar','-sourcepath','kanger-qualification/src','-d',classes,*map(str,extras)],check=True,timeout=45)
    hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path(classes).rglob('*.class'))};(root/(mode+'-class-sha256.json')).write_text(json.dumps(hashes,indent=2)+'\n')
clean=json.loads((root/'clean-class-sha256.json').read_text());journal=json.loads((root/'journal-class-sha256.json').read_text());prior=json.loads(Path('docs/linker-donor-universe/split-class-sha256.json').read_text())
assert all(clean[k]==v for k,v in prior.items())
changedclasses=[k for k in clean if journal.get(k)!=clean[k]]
assert changedclasses and all(k.startswith(('org/kanger/Linker','org/kanger/Mind','org/kanger/factory/TValueFactory')) for k in changedclasses),changedclasses
(root/'class-comparison.json').write_text(json.dumps({'prior_classes_identical':len(prior),'changed_instrumented_classes':changedclasses},indent=2)+'\n')
print('TVALUE_JOURNAL_BUILD_OK',changedclasses)
