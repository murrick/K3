from pathlib import Path
import subprocess,json,hashlib,re,shutil

root=Path(__file__).parent
base='6c45ebe16fbdc3a05fd5ff573bebc2f33ffb601d'
old=Path('docs/tvalue-dirty-buckets')
# Reuse the exact preceding diagnostic instrumentation, without writing its
# committed evidence or changing any native repository source.
prefix=(old/'build.py').read_text().split('for name,body in changed.items():')[0]
env={'__file__':str(root/'build.py')}
exec(prefix,env)
sources=env['sources'];shadow=env['changed']

def ordered(body,target):
    sig='    private boolean '+target+'(IMind m, boolean settleRejectedChild) throws Exception {'
    assert body.count(sig)==1
    wrapper='''    private boolean TARGET(IMind m, boolean settleRejectedChild) throws Exception {
        synchronized(locker) {
            CommitOrderJournal.Event orderEvent=CommitOrderJournal.enter(this,(Mind)m,settleRejectedChild);
            boolean accepted=false;Throwable failure=null;
            try {accepted=orderJournalBody(m,settleRejectedChild);return accepted;}
            catch(Exception|Error problem){failure=problem;throw problem;}
            finally {CommitOrderJournal.exit(orderEvent,accepted,failure);}
        }
    }

'''.replace('TARGET',target)
    return body.replace(sig,wrapper+sig.replace(target+'(','orderJournalBody('))

def recover(body,mode,name):
    if name=='Mind.java':
        target='commit' if mode=='order' else 'journalCommitBody'
        body=re.sub(r'    private boolean '+target+r'\(IMind m, boolean settleRejectedChild\) throws Exception \{\n.*?\n    \}\n\n','',body,count=1,flags=re.S)
        body=body.replace('private boolean orderJournalBody(','private boolean '+target+'(')
        if mode=='shadow':
            body=re.sub(r'    private boolean commit\(IMind m, boolean settleRejectedChild\) throws Exception \{\n.*?\n    \}\n\n','',body,count=1,flags=re.S)
            body=re.sub(r'    public void release\(IMind m\) throws Exception \{\n.*?\n    \}\n\n','',body,count=1,flags=re.S)
            body=re.sub(r'    public void setUnitDeleted\(IUnit unit, boolean on\) \{\n.*?\n    \}\n\n','',body,count=1,flags=re.S)
            body=body.replace('private void journalSetUnitDeletedBody(','public void setUnitDeleted(').replace('private boolean journalCommitBody(','private boolean commit(').replace('private void journalReleaseBody(','public void release(')
            body=body.replace('        TValueDirtyJournal.constructed(this);\n','')
    elif mode=='shadow' and name=='Linker.java':body=re.sub(r'        (?:    )?TValueDirtyJournal\.(?:linkStart|observe)\(.*?;\n','',body)
    elif mode=='shadow':
        body=body.replace('        long journalMark=cache.mark();\n        org.kanger.TValueDirtyJournal.mark(mind);\n        return journalMark;','        return cache.mark();')
        body=re.sub(r'(?m)^ +org.kanger.TValueDirtyJournal\.(?:reset|complete|touch|promoted)\(mind.*?;\n','',body)
    return body

assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-qualification/src','.github'])
listing=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
runners=['KangerLinkerDonorScopeSafetyRunner','LatentSolveSyncTransactionRunner','KangerMindCommitExceptionAtomicitySafetyRunner']
helpers=[old/(n+'.java') for n in ['TValueDirtyJournal','DirtyJournalRunner','TValueDirtyJournalSafetyRunner','TValueDirtyGapSafetyRunner','TValueOrderedPublicationRunner']]
helpers += [root/(n+'.java') for n in ['CommitOrderJournal','PublicationFixture','ThreadedPublicationCaptureRunner','CleanPublicationReplayRunner']]
structure={};instrumentation={}
for mode in ('clean','order','shadow'):
    classes=Path('../build/tvalue-publication-replay-'+mode)
    if classes.exists():shutil.rmtree(classes)
    paths=listing.copy()
    if mode!='clean':
        changed=dict(sources if mode=='order' else shadow)
        changed['Mind.java']=ordered(changed['Mind.java'],'commit' if mode=='order' else 'journalCommitBody')
        temp=Path('../build/tvalue-publication-replay-'+mode+'-source')
        structure[mode]={};instrumentation[mode]={}
        for name,body in changed.items():
            assert recover(body,mode,name)==sources[name],(mode,name)
            path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body)
            paths[paths.index('kanger/src/org/kanger/'+name)]=str(path)
            structure[mode][name]={'all_original_code_recovered_byte_for_byte':True,'sha256':hashlib.sha256(sources[name].encode()).hexdigest()}
            instrumentation[mode][name]=hashlib.sha256(body.encode()).hexdigest()
    paths+=list(map(str,helpers));path=root/(mode+'-sources.txt');path.write_text('\n'.join(paths)+'\n')
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',str(classes),'@'+str(path)],check=True,timeout=120)
    extras=[Path('kanger-qualification/src/org/kanger/'+r+'.java') for r in runners]+[Path('docs/linker-donor-universe/ExactCandidateReplayRunner.java')]
    # Compile all preceding extras so the clean rebuild's complete prior-class
    # hash set remains an oracle, independently of the new runner selection.
    extra_names=['KangerLinkerRuleOrderingSafetyRunner','KangerLinkerCheckpointBalanceSafetyRunner','KangerCompletedHypothesisContractRunner','KangerRuleCandidateConcurrencyRunner','LatentSubstitutionCorpusRunner']
    extras += [Path('kanger-qualification/src/org/kanger/'+r+'.java') for r in extra_names]+[Path('docs/linker-donor-universe/FrontierDependencyWitness.java'),Path('docs/linker-dependency-lifecycle/DependencyLifecycleWitness.java')]
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(classes)+':lib/jline-3.13.0.jar','-sourcepath','kanger-qualification/src','-d',str(classes),*map(str,extras)],check=True,timeout=60)
    hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(classes.rglob('*.class'))};(root/(mode+'-class-sha256.json')).write_text(json.dumps(hashes,indent=2)+'\n')
clean=json.loads((root/'clean-class-sha256.json').read_text());prior=json.loads((old/'clean-class-sha256.json').read_text());assert all(clean[k]==v for k,v in prior.items())
comparison={'all_prior_clean_classes_identical':len(prior)}
for mode in ('order','shadow'):
    current=json.loads((root/(mode+'-class-sha256.json')).read_text());changed=[k for k,v in clean.items() if current[k]!=v];comparison[mode+'_changed_shared_classes']=changed
    assert changed and all(k.startswith(('org/kanger/Mind','org/kanger/Linker','org/kanger/factory/TValueFactory')) for k in changed)
prior_shadow=json.loads((old/'journal-class-sha256.json').read_text());current=json.loads((root/'shadow-class-sha256.json').read_text());assert all(current[k]==v for k,v in prior_shadow.items() if not k.startswith('org/kanger/Mind'))
comparison['prior_shadow_classes_except_Mind_identical']=True
(root/'class-comparison.json').write_text(json.dumps(comparison,indent=2)+'\n');(root/'structural-check.json').write_text(json.dumps(structure,indent=2)+'\n');(root/'instrumentation.json').write_text(json.dumps({'base':base,'temporary_source_sha256':instrumentation,'scope':'diagnostic order wrapper under native monitor; unchanged native bodies; preceding TValue hooks in shadow build only'},indent=2)+'\n')
print('PUBLICATION_REPLAY_BUILD_OK',json.dumps(comparison))
