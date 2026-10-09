from pathlib import Path
import json,hashlib,subprocess,shutil
p=Path(__file__).parent;native=Path('../K3-smart-native');head='5d5f6aff271f1abf371fbde55d637744c53f0bc3'
assert subprocess.check_output(['git','-C',str(native),'rev-parse','HEAD'],text=True).strip()==head
assert not subprocess.check_output(['git','-C',str(native),'status','--porcelain'])
prior=json.loads(Path('docs/tvalue-smart-base/build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in prior['source_sha256'].items())
changes={};proof={};mapping={}
for name in ['Mind.java','factory/TValueFactory.java','units/TValue.java']:
 original=(native/'kanger/src/org/kanger'/name).read_text();body=original;edits=[]
 def edit(old,new):
  global body
  assert body.count(old)==1,(name,old,body.count(old));body=body.replace(old,new,1);edits.append({'old':old,'new':new})
 if name=='Mind.java':
  edit('        init();\n    }','        init();\n        NativeJournalHooks.constructed(this);\n    }')
  old='            if (!initialized) {\n                parent.abortTransactionStart();\n            }\n        }\n    }'
  edit(old,old[:-5]+'        NativeJournalHooks.constructed(this);\n    }')
  sig='    private boolean commit(IMind m, boolean settleRejectedChild) throws Exception {'
  wrapper='''    private boolean commit(IMind m, boolean settleRejectedChild) throws Exception {
        int before = pendingTransactionCount();
        NativeJournalHooks.beginSettlement(this);
        try { return journalCommitBody(m,settleRejectedChild); }
        finally {
            NativeJournalHooks.endSettlement(this);
            if(pendingTransactionCount() < before) NativeJournalHooks.retire((Mind)m);
        }
    }

'''
  edit(sig,wrapper+sig.replace('commit(','journalCommitBody('))
  sig='    public void release(IMind m) throws Exception {'
  wrapper='''    public void release(IMind m) throws Exception {
        int before = pendingTransactionCount();
        NativeJournalHooks.beginSettlement(this);
        try { journalReleaseBody(m); }
        finally {
            NativeJournalHooks.endSettlement(this);
            if(pendingTransactionCount() < before) NativeJournalHooks.retire((Mind)m);
        }
    }

'''
  edit(sig,wrapper+sig.replace('public void release(','private void journalReleaseBody('))
  sig='    public void setUnitDeleted(IUnit unit, boolean on) {'
  wrapper='''    public void setUnitDeleted(IUnit unit, boolean on) {
        journalSetUnitDeletedBody(unit,on);
        if(unit instanceof org.kanger.units.TValue)NativeJournalHooks.touch(this,(org.kanger.units.TValue)unit,"visibility");
    }

'''
  edit(sig,wrapper+sig.replace('public void setUnitDeleted(','private void journalSetUnitDeletedBody('))
 elif name=='factory/TValueFactory.java':
  old='            indexInitialized = base != null;\n        }\n    }';edit(old,'            indexInitialized = base != null;\n        }\n        org.kanger.NativeJournalHooks.reset(mind);\n    }')
  edit('        return cache.mark();','        long journalMark=cache.mark();\n        org.kanger.NativeJournalHooks.mark(mind);\n        return journalMark;')
  for start,end in [('    public long commit()','    public long release()'),('    public long release()','    public TValue set(')]:
   chunk=body[body.index(start):body.index(end,body.index(start))];assert chunk.count('        return result;')==1
   edit(chunk,chunk.replace('        return result;','        org.kanger.NativeJournalHooks.complete(mind);\n        return result;'))
  chunk=body[body.index('    public synchronized TValue add('):body.index('    public TValue get(TVariable')];assert chunk.count('        return t;')==1
  edit(chunk,chunk.replace('        return t;','        org.kanger.NativeJournalHooks.touch(mind,t,"add");\n        return t;'))
  old='            cache.delete(((IUnit) o).getId());';edit(old,old+'\n            org.kanger.NativeJournalHooks.touch(mind,(TValue)o,"remove");')
  old='        action = action || base.isAction();';edit(old,old+'\n        org.kanger.NativeJournalHooks.promoted(mind,base);')
 else:
  for signature,method,args in [('public void setValue(Term value)','setValue','value'),('public void setId(long id)','setId','id'),('public void setTVar(TVariable tVar)','setTVar','tVar'),('public void setMindId(long mindId)','setMindId','mindId')]:
   sig='    '+signature+' {';wrapper='    '+signature+' {\n        long oldId=getId(),oldVariable=getTVarId(),oldTerm=getValueId();\n        try { journal'+method+'Body('+args+'); }\n        finally { org.kanger.NativeJournalHooks.metadata(this,oldId,oldVariable,oldTerm,"'+method+'"); }\n    }\n\n'
   edit(sig,wrapper+sig.replace('public void '+method+'(','private void journal'+method+'Body('))
 recovered=body
 for e in reversed(edits):assert recovered.count(e['new'])==1;recovered=recovered.replace(e['new'],e['old'],1)
 assert recovered==original,name
 target=p/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(body);mapping[str(native/'kanger/src/org/kanger'/name)]=str(target)
 changes[name]=edits;proof[name]={'original_sha256':hashlib.sha256(original.encode()).hexdigest(),'instrumented_sha256':hashlib.sha256(body.encode()).hexdigest(),'original_code_recovered_byte_for_byte':True}
(p/'changes.json').write_text(json.dumps(changes,indent=2)+'\n');(p/'structural-check.json').write_text(json.dumps(proof,indent=2)+'\n')
listing=Path('docs/tvalue-smart-base/sources.txt').read_text().splitlines()+[str(p/'NativeJournalHooks.java'),str(p/'AutoSettlementRunner.java')]
classes={};commands={};sources={}
for mode in ['clean','hooked']:
 out=Path('../build/tvalue-native-hooks-'+mode)
 if out.exists():shutil.rmtree(out)
 out.mkdir(parents=True);paths=[mapping.get(x,x) if mode=='hooked' else x for x in listing];source_list=p/(mode+'-sources.txt');source_list.write_text('\n'.join(paths)+'\n');cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',str(out),'@'+str(source_list)];subprocess.run(cmd,check=True);commands[mode]=cmd;sources[mode]={x:hashlib.sha256(Path(x).read_bytes()).hexdigest() for x in paths};classes[mode]={str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')}
assert all(classes['clean'][k]==v for k,v in prior['class_sha256'].items());assert classes['clean'].keys()==classes['hooked'].keys()
changed=[k for k in classes['clean'] if classes['clean'][k]!=classes['hooked'][k]];assert changed and all(k.startswith(('org/kanger/Mind','org/kanger/factory/TValueFactory','org/kanger/units/TValue')) for k in changed),changed
b={'parent':'cd4aa7d83165d0b18bf2bab4cb54085d58d13235','develop_head':head,'commands':commands,'source_sha256':sources,'class_sha256':classes,'changed_native_classes':changed,'unchanged_prior_clean_classes_verified':len(prior['class_sha256']),'diagnostic_implementation_unchanged':True,'all_original_native_code_recovered_byte_for_byte':True};(p/'build-validation.json').write_text(json.dumps(b,indent=2)+'\n');print('NATIVE_HOOKS_BUILD_OK',changed)
