from pathlib import Path
import subprocess,json,hashlib,re,shutil

root=Path(__file__).parent
base='ca2d1b081d5e49cbe98bfe7096abe8eb97d1df22'
old=Path('docs/tvalue-dirty-buckets')
# Reuse the exact preceding diagnostic instrumentation, without writing its
# committed evidence or changing any native repository source.
prefix=(old/'build.py').read_text().split('for name,body in changed.items():')[0]
env={'__file__':str(root/'build.py')}
exec(prefix,env)
sources=env['sources'];shadow=env['changed']


base='ca2d1b081d5e49cbe98bfe7096abe8eb97d1df22'
sources['units/TValue.java']=Path('kanger/src/org/kanger/units/TValue.java').read_text()
shadow['units/TValue.java']=sources['units/TValue.java']
wrappers={}
specs=[
    ('    public void setValue(Term value) {','value',False),
    ('    public void setTVar(TVariable tVar) {','tVar',False),
    ('    public void setPersistentReferences(long valueId, long tVarId) {','valueId,tVarId',False),
    ('    public void setId(long id) {','id',False),
    ('    public TValue apply(ByteBuffer packet) throws OutOfBufferException {','packet',True),
    ('    public TValue applyMap(Map<String, Object> map) throws Exception {','map',True)]
for sig,params,returns in specs:
    name=sig.split('(')[0].split()[-1]
    wrapper=sig+'\n        long journalId=this.id,journalVariable=this.tVarId,journalTerm=this.valueId;\n        try {'+('return ' if returns else '')+'metadataBody_'+name+'('+params+');}\n        finally {org.kanger.TValueDirtyJournal.metadata(this,journalId,journalVariable,journalTerm,"'+name+'");}\n    }\n\n'
    bodySig=sig.replace('public ','private ',1).replace(name+'(','metadataBody_'+name+'(',1)
    assert shadow['units/TValue.java'].count(sig)==1
    shadow['units/TValue.java']=shadow['units/TValue.java'].replace(sig,wrapper+bodySig)
    wrappers[name]=(wrapper,bodySig,sig)
def recover(body,mode,name):
    if name=='units/TValue.java':
        for wrapper,bodySig,sig in wrappers.values():body=body.replace(wrapper,'').replace(bodySig,sig)
        return body
    if name=='Mind.java':
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
helpers=[old/(n+'.java') for n in ['DirtyJournalRunner','TValueDirtyJournalSafetyRunner','TValueDirtyGapSafetyRunner','TValueOrderedPublicationRunner']]
helpers += [Path('docs/tvalue-publication-replay')/(n+'.java') for n in ['CommitOrderJournal','PublicationFixture','ThreadedPublicationCaptureRunner','CleanPublicationReplayRunner']]
helpers += [root/'TValueDirtyJournal.java',Path('docs/tvalue-metadata-observation')/'TValueMetadataSafetyRunner.java',Path('docs/tvalue-metadata-observation')/'TValueMetadataNativeRunner.java',Path('docs/tvalue-metadata-observation')/'TValueMetadataGapRunner.java',Path('docs/tvalue-persistence-boundaries')/'TValuePersistenceRunner.java',Path('docs/tvalue-persistence-boundaries')/'TValuePersistenceGapRunner.java',Path('docs/tvalue-persistence-boundaries')/'TValueOwnerReadWitness.java',root/'ResidentTValueRead.java',root/'TValueOwnerPureRunner.java',root/'TValueResidentBoundaryRunner.java']
structure={};instrumentation={}
for mode in ('clean','shadow'):
    classes=Path('../build/tvalue-owner-pure-observation-'+mode)
    if classes.exists():shutil.rmtree(classes)
    paths=listing.copy()
    if mode!='clean':
        changed=dict(shadow)
        temp=Path('../build/tvalue-owner-pure-observation-'+mode+'-source')
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
clean=json.loads((root/'clean-class-sha256.json').read_text());prior=json.loads(Path('docs/tvalue-persistence-boundaries/clean-class-sha256.json').read_text());assert all(clean[k]==v for k,v in prior.items() if not k.startswith("org/kanger/TValueDirtyJournal"))
comparison={'prior_clean_classes_identical':sum(not k.startswith('org/kanger/TValueDirtyJournal') for k in prior),'changed_prior_clean_helper_family':'TValueDirtyJournal'}
for mode in ('shadow',):
    current=json.loads((root/(mode+'-class-sha256.json')).read_text());changed=[k for k,v in clean.items() if current[k]!=v];comparison[mode+'_changed_shared_classes']=changed
    assert changed and all(k.startswith(('org/kanger/Mind','org/kanger/Linker','org/kanger/factory/TValueFactory','org/kanger/units/TValue')) for k in changed)
prior_shadow=json.loads(Path('docs/tvalue-persistence-boundaries/shadow-class-sha256.json').read_text());current=json.loads((root/'shadow-class-sha256.json').read_text());checked={k:v for k,v in prior_shadow.items() if not k.startswith('org/kanger/TValueDirtyJournal')};assert all(current[k]==v for k,v in checked.items());comparison['prior_shadow_classes_except_journal_helper_identical']=len(checked)
(root/'class-comparison.json').write_text(json.dumps(comparison,indent=2)+'\n')
(root/'structural-check.json').write_text(json.dumps(structure,indent=2)+'\n')
(root/'instrumentation.json').write_text(json.dumps({'base':base,'temporary_source_sha256':instrumentation,'scope':'unchanged six setters and previous hooks; helper reads resident state without getData(Mind), index initialization or storage; native bodies byte-identical'},indent=2)+'\n')
print('TVALUE_OWNER_PURE_BUILD_OK',json.dumps(comparison))
